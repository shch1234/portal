package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionSummaryRepository;
import com.weili.iot_portal.domain.device.req.DeviceProductionStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceProductionStatisticsRespVO;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceProductionSummaryService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备产量汇总服务实现（带工厂分组、批次、检查点）
 */
@Slf4j
@Service
public class DeviceProductionSummaryService implements IDeviceProductionSummaryService {

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceProductionRecordRepository productionRecordRepository;
    private final DeviceProductionSummaryRepository productionSummaryRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final ICheckpointService<CheckpointData> checkpointService;

    public DeviceProductionSummaryService(DeviceInfoRepository deviceInfoRepository,
                                          DeviceProductionRecordRepository productionRecordRepository,
                                          DeviceProductionSummaryRepository productionSummaryRepository,
                                          IShiftCalculationService shiftCalculationService,
                                          @Qualifier("deviceProductionSummaryCheckpointService")
                                          ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.productionRecordRepository = productionRecordRepository;
        this.productionSummaryRepository = productionSummaryRepository;
        this.shiftCalculationService = shiftCalculationService;
        this.checkpointService = checkpointService;
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(long statisticsTimeSeconds,
                                                              int batchSize,
                                                              long timeoutMillis) {
        List<DeviceInfoDO> allDevices = deviceInfoRepository.findAllActive();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0, skip = 0, error = 0;

        Map<Long, List<DeviceInfoDO>> devicesByFactory = allDevices.stream()
                .filter(d -> d.getOrgFactoryId()!=null)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        if (allDevices.size() > filtered) {
            skip += (int) (allDevices.size() - filtered);
            log.warn("产量汇总: 有 {} 个设备未关联工厂，已跳过", allDevices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : devicesByFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> devices = entry.getValue();
            try {
                BatchProcessResult factoryResult = processFactoryDevicesWithCheckpoint(
                        factoryId, devices, statisticsTimeSeconds, batchSize, timeoutMillis);
                success += factoryResult.getSuccessCount();
                skip += factoryResult.getSkipCount();
                error += factoryResult.getErrorCount();
            } catch (Exception e) {
                error += devices.size();
                log.error("处理工厂失败: factoryId={}, deviceCount={}", factoryId, devices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(Long factoryId,
                                                                  List<DeviceInfoDO> devices,
                                                                  long statisticsTimeSeconds,
                                                                  int batchSize,
                                                                  long timeoutMillis) {
        Set<Long> processedIds = checkpointService.getProcessedDeviceIds(factoryId, statisticsTimeSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        if (!processedIds.isEmpty()) {
            log.info("产量汇总从检查点恢复: factoryId={}, 已处理={}, 剩余={}",
                    factoryId, processedIds.size(), remaining.size());
        }

        int success = 0, skip = 0, error = 0;
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            List<Long> batchProcessed = new ArrayList<>();
            for (DeviceInfoDO device : batch) {
                try {
                    boolean processed = processSingleDevice(device, statisticsTimeSeconds);
                    if (processed) {
                        success++;
                    } else {
                        skip++;
                    }
                    processedIds.add(device.getId());
                    batchProcessed.add(device.getId());
                } catch (Exception e) {
                    error++;
                    log.error("产量汇总失败 deviceId={}", device.getId(), e);
                }
            }

            // 保存检查点
            checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, new ArrayList<>(processedIds));

            // 超时检查
            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("产量汇总超时: factoryId={}, processed={}, remaining={}",
                        factoryId, processedIds.size(), devices.size() - processedIds.size());
                return BatchProcessResult.incomplete(success, skip, error);
            }
        }

        checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
        return BatchProcessResult.completed(success, skip, error);
    }

    /**
     * 处理单台设备在统计时间点前已结束的班次
     */
    protected boolean processSingleDevice(DeviceInfoDO device, long statisticsTimeSeconds) {
        long statisticsTimeMs = statisticsTimeSeconds * 1000;
        ShiftTimeRange range = shiftCalculationService.calculateShiftRange(
                device.getOrgFactoryId(), device.getId(), statisticsTimeMs);

        if (range == null || range.getEndTs() == null) {
            return false;
        }
        if (range.getEndTs() > statisticsTimeMs) {
            // 班次尚未结束
            return false;
        }

        long shiftStartSec = range.getStartTs() / 1000;
        long shiftEndSec = range.getEndTs() / 1000;
        if (shiftEndSec <= shiftStartSec) {
            return false;
        }

        long count = productionRecordRepository.countCompletedInRange(device.getId(), shiftStartSec, shiftEndSec);

        LocalDate shiftDate = Instant.ofEpochMilli(range.getStartTs())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        upsertSummary(device.getId(), shiftDate, range.getShiftCode(), shiftStartSec, shiftEndSec, count, statisticsTimeSeconds);
        return true;
    }

    private void upsertSummary(Long deviceId,
                               LocalDate shiftDate,
                               Integer shiftCode,
                               long shiftStartSec,
                               long shiftEndSec,
                               long partCount,
                               long calculatedTimeSec) {
        DeviceProductionSummaryDO existing = productionSummaryRepository.findByShift(deviceId, shiftDate, shiftCode);
        if (existing == null) {
            DeviceProductionSummaryDO summary = new DeviceProductionSummaryDO();
            summary.setDeviceInfoId(deviceId);
            summary.setShiftDate(shiftDate);
            summary.setShiftCode(shiftCode);
            summary.setShiftStartTs(shiftStartSec);
            summary.setShiftEndTs(shiftEndSec);
            summary.setPartCount((int) partCount);
            summary.setQualifiedCount((int) partCount);
            summary.setDefectCount(0);
            summary.setIsFinalized(true);
            summary.setCalculatedTime(calculatedTimeSec);
            productionSummaryRepository.insert(summary);
        } else {
            existing.setShiftStartTs(shiftStartSec);
            existing.setShiftEndTs(shiftEndSec);
            existing.setPartCount((int) partCount);
            existing.setQualifiedCount((int) partCount);
            existing.setDefectCount(existing.getDefectCount() == null ? 0 : existing.getDefectCount());
            existing.setIsFinalized(true);
            existing.setCalculatedTime(calculatedTimeSec);
            productionSummaryRepository.update(existing);
        }
    }

    @Override
    public DeviceProductionStatisticsRespVO getDeviceProductionStatistics(DeviceProductionStatisticsReqVO reqVO) {
        DeviceProductionStatisticsRespVO respVO = new DeviceProductionStatisticsRespVO();

        Long deviceInfoId = reqVO.getDeviceInfoId();
        Optional<DeviceInfoDO> optional = deviceInfoRepository.findById(deviceInfoId);
        if (optional.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        DeviceInfoDO deviceInfoDO = optional.get();

        // 获取当前时间（包括时分秒），用于获取当前所在的班次信息
        Instant now = Instant.now();
        long nowSeconds = now.getEpochSecond();

        // 根据当前时间获取所在的班次信息
        ShiftDateAndCode currentShiftInfo = shiftCalculationService.getShiftDateAndCode(
                deviceInfoDO.getOrgFactoryId(),
                deviceInfoId,
                nowSeconds);

        // 使用班次的 shiftDate 作为查询的结束日期
        LocalDate endDate = currentShiftInfo.shiftDate();
        Integer currentShiftCode = currentShiftInfo.shiftCode();

        // 1. 查询当前班次的加工数量（从 device_production_record 表汇总）
        long todayCount = productionRecordRepository.countByDate(deviceInfoId, endDate, currentShiftCode);
        respVO.setTodayProductionCount((int) todayCount);

        // 2. 根据时间范围确定查询的起止日期
        LocalDate startDate;
        if ("WEEK".equalsIgnoreCase(reqVO.getTimeRange())) {
            // 近一周：今天往前推6天，共7天
            startDate = endDate.minusDays(6);
        } else if ("MONTH".equalsIgnoreCase(reqVO.getTimeRange())) {
            // 近一个月：今天往前推29天，共30天（但图表只显示15个点）
            startDate = endDate.minusDays(29);
        } else {
            throw new IllegalArgumentException("不支持的时间范围: " + reqVO.getTimeRange());
        }

        // 获取起始时间对应的班次信息
        ShiftDateAndCode startShiftDateAndCode = shiftCalculationService.getShiftDateAndCode(
                deviceInfoDO.getOrgFactoryId(),
                deviceInfoId,
                startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().getEpochSecond());

        // 3. 从 device_production_summary 查询日期范围内的汇总数据
        List<DeviceProductionSummaryDO> summaryList = productionSummaryRepository.findByDateRange(
                deviceInfoId,
                startShiftDateAndCode.shiftCode(),
                startShiftDateAndCode.shiftDate(),
                endDate);

        // 4. 按日期分组汇总（一天可能有多个班次）
        Map<LocalDate, Integer> dailyProductionMap = summaryList.stream()
                .collect(Collectors.groupingBy(
                        DeviceProductionSummaryDO::getShiftDate,
                        Collectors.summingInt(s -> s.getPartCount() != null ? s.getPartCount() : 0)
                ));

        // 5. 构建图表数据（按日期排序）
        List<DeviceProductionStatisticsRespVO.ProductionDetailVO> detailList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        int index = 1;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DeviceProductionStatisticsRespVO.ProductionDetailVO detail =
                    new DeviceProductionStatisticsRespVO.ProductionDetailVO();
            detail.setIndex(index++);
            detail.setDateLabel(date.format(formatter));

            Integer productionCount = dailyProductionMap.getOrDefault(date, 0);
            detail.setProductionCount(productionCount);
            detail.setQualifiedCount(productionCount); // 默认合格数量等于加工数量
            detail.setDefectCount(0);

            detailList.add(detail);
        }

        respVO.setProductionDetails(detailList);

        return respVO;
    }
}

