package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceShiftSummaryService;
import com.weili.iot_portal.service.device.IDeviceStateStatisticsService;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CompensationResult;
import com.weili.iot_portal.domain.ingestion.ProcessResult;
import com.weili.iot_portal.domain.ingestion.StateStatistics;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备班次汇总服务实现
 */
@Slf4j
@Service
public class DeviceShiftSummaryService implements IDeviceShiftSummaryService {

    private static final String CALCULATION_SOURCE_SCHEDULED = "SCHEDULED";

    private final DeviceStateSummaryRepository stateSummaryRepository;
    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository stateRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final IShiftConfigService shiftConfigService;
    private final IDeviceStateStatisticsService stateStatisticsService;
    private final ICheckpointService<ICheckpointService.CheckpointData> checkpointService;

    @Autowired
    public DeviceShiftSummaryService(DeviceStateSummaryRepository stateSummaryRepository,
                                     DeviceInfoRepository deviceInfoRepository,
                                     DeviceStateRecordRepository stateRecordRepository,
                                     IShiftCalculationService shiftCalculationService,
                                     IShiftConfigService shiftConfigService,
                                     IDeviceStateStatisticsService stateStatisticsService,
                                     @Qualifier("deviceStateSummaryCheckpointService")
                                     ICheckpointService<ICheckpointService.CheckpointData> checkpointService) {
        this.stateSummaryRepository = stateSummaryRepository;
        this.deviceInfoRepository = deviceInfoRepository;
        this.stateRecordRepository = stateRecordRepository;
        this.shiftCalculationService = shiftCalculationService;
        this.shiftConfigService = shiftConfigService;
        this.stateStatisticsService = stateStatisticsService;
        this.checkpointService = checkpointService;
    }

    @Override
    public boolean shouldProcessShift(ShiftTimeRange shiftRange, long statisticsTimeSeconds) {
        long shiftEndTimeSeconds = shiftRange.getEndTs() / 1000;
        return statisticsTimeSeconds >= shiftEndTimeSeconds;
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis) {

        List<DeviceInfoDO> allDevices = deviceInfoRepository.findAllActive();
        if (allDevices == null || allDevices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0;
        int skip = 0;
        int error = 0;

        Map<Long, List<DeviceInfoDO>> devicesByFactory = allDevices.stream()
                .filter(device -> device.getOrgFactoryId() != null)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        if (allDevices.size() > filtered) {
            skip += (int) (allDevices.size() - filtered);
            log.warn("设备状态汇总: 有 {} 个设备未关联工厂，已跳过", allDevices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> factoryEntry : devicesByFactory.entrySet()) {
            Long factoryId = factoryEntry.getKey();
            List<DeviceInfoDO> devices = factoryEntry.getValue();
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
    public CompensationResult compensatePendingSummaries(int compensationDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(compensationDays);

        List<DeviceStateSummaryDO> pending = stateSummaryRepository.selectPending(startDate, endDate);
        if (pending != null && !pending.isEmpty()) {
            log.info("补偿任务: 发现未完成汇总 {} 条，窗口 {} - {}", pending.size(), startDate, endDate);
        }

        int success = 0;
        int skip = 0;
        int error = 0;

        if (pending != null) {
            for (DeviceStateSummaryDO summary : pending) {
                try {
                    boolean processed = recalculateSummary(summary);
                    if (processed) {
                        success++;
                    } else {
                        skip++;
                    }
                } catch (Exception e) {
                    error++;
                    log.error("补偿处理失败: summaryId={}, deviceId={}, shiftDate={}, shiftCode={}",
                            summary.getId(), summary.getDeviceInfoId(),
                            summary.getSummaryDate(), summary.getShiftCode(), e);
                }
            }
        }

        return new CompensationResult(success, skip, error);
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis) {

        // 加载检查点，获取已处理的设备ID
        Set<Long> processedDeviceIds = checkpointService.getProcessedDeviceIds(
                factoryId, statisticsTimeSeconds);

        // 过滤已处理的设备
        List<DeviceInfoDO> remainingDevices = devices.stream()
                .filter(device -> !processedDeviceIds.contains(device.getId()))
                .collect(Collectors.toList());

        if (remainingDevices.isEmpty()) {
            // 所有设备已处理，清除检查点
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        // 分批处理剩余设备
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        List<Long> newProcessedIds = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < remainingDevices.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remainingDevices.size());
            List<DeviceInfoDO> batch = remainingDevices.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    ProcessResult result = processDeviceShiftComplete(
                            device, statisticsTimeSeconds);
                    if (result.isProcessed()) {
                        successCount++;
                        newProcessedIds.add(device.getId());
                        processedDeviceIds.add(device.getId());
                    } else {
                        skipCount++;
                        log.debug("跳过设备: factoryId={}, deviceId={}, reason={}",
                                factoryId, device.getId(), result.getSkipReason());
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("处理设备失败: factoryId={}, deviceId={}, deviceCode={}",
                            factoryId, device.getId(), device.getDeviceCode(), e);
                    // 继续处理下一个设备，不中断
                }
            }

            // 每批处理完后更新检查点
            if (!newProcessedIds.isEmpty()) {
                List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
                checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, allProcessedIds);
                newProcessedIds.clear();
            }

            // 检查是否超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMillis) {
                log.info("处理超时，保存检查点: 工厂={}, 已处理={}, 剩余={}",
                        factoryId, processedDeviceIds.size(), remainingDevices.size() - processedDeviceIds.size());
                return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
            }
        }

        // 所有设备都处理完成，清除检查点
        if (processedDeviceIds.size() >= devices.size()) {
            checkpointService.clearCheckpoint(factoryId, statisticsTimeSeconds);
            return BatchProcessResult.completed(successCount, skipCount, errorCount);
        } else {
            // 还有未处理的设备，保存检查点
            List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
            checkpointService.saveCheckpoint(factoryId, statisticsTimeSeconds, allProcessedIds);
            return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessResult processDeviceShiftComplete(
            DeviceInfoDO device,
            long statisticsTimeSeconds) {

        // 1. 查询设备在当前时间的班次配置（无配置则使用默认配置）
        var shiftConfig = shiftConfigService.getCurrentConfiguration(
                device.getOrgFactoryId(), device.getId(), statisticsTimeSeconds * 1000L);

        // 2. 计算已结束的班次
        // 统计时间点应该落在刚结束的班次内，所以需要找到前一个班次
        ShiftTimeRange previousShiftRange = shiftCalculationService.calculatePreviousShiftRange(
                device.getOrgFactoryId(), device.getId(), statisticsTimeSeconds);

        if (previousShiftRange == null) {
            // 无法计算前一个班次，跳过
            return ProcessResult.skipped("无法计算前一个班次");
        }

        // 3. 检查班次是否已经结束
        if (!shouldProcessShift(previousShiftRange, statisticsTimeSeconds)) {
            // 班次还未到统计时间，跳过
            return ProcessResult.skipped("班次未到统计时间");
        }

        // 4. 查询班次状态数据
        List<DeviceStateRecordDO> stateRecords = stateRecordRepository.selectByRange(
                device.getId(),
                previousShiftRange.getStartTs() / 1000,
                previousShiftRange.getEndTs() / 1000);

        // 5. 计算状态统计
        Map<String, StateStatistics> stateStats = stateStatisticsService.calculateStatistics(
                stateRecords,
                previousShiftRange.getStartTs() / 1000,
                previousShiftRange.getEndTs() / 1000);

        // 6. 计算班次日期
        long shiftEndTimeSeconds = previousShiftRange.getEndTs() / 1000;
        LocalDate shiftDate = LocalDate.ofInstant(
                Instant.ofEpochSecond(shiftEndTimeSeconds),
                ZoneId.systemDefault());

        // 7. 保存或更新汇总记录
        return processDeviceShift(device, shiftConfig, previousShiftRange, shiftDate, stateStats);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessResult processDeviceShift(
            DeviceInfoDO device,
            DeviceShiftConfigDO config,
            ShiftTimeRange shiftRange,
            LocalDate shiftDate,
            Map<String, StateStatistics> stateStats) {

        // 检查是否已经统计过（避免重复统计）
        DeviceStateSummaryDO existingSummary = stateSummaryRepository.findByShift(
                device.getId(), shiftDate, shiftRange.getShiftCode());

        if (existingSummary != null && Boolean.TRUE.equals(existingSummary.getIsFinalized())) {
            // 已经统计过且已确定，跳过
            return ProcessResult.skipped("已统计过");
        }

        // 保存或更新汇总记录
        saveOrUpdateSummary(device.getId(), device.getOrgFactoryId(), shiftDate, shiftRange, stateStats);

        return ProcessResult.processed();
    }


    @Override
    public void forceUpdateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats) {
        // 强制更新，不检查是否已统计过
        saveOrUpdateSummary(deviceId, orgFactoryId, shiftDate, shiftRange, stateStats);
    }

    /**
     * 保存或更新汇总记录
     */
    private void saveOrUpdateSummary(
            Long deviceId,
            Long orgFactoryId,
            LocalDate shiftDate,
            ShiftTimeRange shiftRange,
            Map<String, StateStatistics> stateStats) {

        DeviceStateSummaryDO summary = stateSummaryRepository.findByShift(
                deviceId, shiftDate, shiftRange != null ? shiftRange.getShiftCode() : null);

        boolean exists = summary != null;
        if (!exists) {
            summary = new DeviceStateSummaryDO();
            summary.setDeviceInfoId(deviceId);
            summary.setOrgFactoryId(orgFactoryId);
            summary.setSummaryDate(shiftDate);
            if (shiftRange != null) {
                summary.setShiftCode(shiftRange.getShiftCode());
            }
        } else {
            // 更新时也更新orgFactoryId（防止设备迁移到其他工厂）
            summary.setOrgFactoryId(orgFactoryId);
        }

        if (shiftRange != null) {
            // 更新字段
            summary.setShiftStartTs(shiftRange.getStartTs() / 1000);
            summary.setShiftEndTs(shiftRange.getEndTs() / 1000);
        }

        summary.setIsFinalized(true);
        summary.setCalculatedTime(System.currentTimeMillis() / 1000);
        summary.setCalculationSource(CALCULATION_SOURCE_SCHEDULED);

        // 设置状态统计
        StateStatistics working = stateStats.getOrDefault(DeviceStateEnum.WORKING.name(),
                new StateStatistics(DeviceStateEnum.WORKING.name(), 0, 0));
        StateStatistics standby = stateStats.getOrDefault(DeviceStateEnum.STANDBY.name(),
                new StateStatistics(DeviceStateEnum.STANDBY.name(), 0, 0));
        StateStatistics fault = stateStats.getOrDefault(DeviceStateEnum.FAULT.name(),
                new StateStatistics(DeviceStateEnum.FAULT.name(), 0, 0));
        StateStatistics shutdown = stateStats.getOrDefault(DeviceStateEnum.SHUTDOWN.name(),
                new StateStatistics(DeviceStateEnum.SHUTDOWN.name(), 0, 0));
        StateStatistics missing = stateStats.getOrDefault("MISSING",
                new StateStatistics("MISSING", 0, 0));

        summary.setWorkingDurationS((int) working.durationSeconds);
        summary.setStandbyDurationS((int) standby.durationSeconds);
        summary.setFaultDurationS((int) fault.durationSeconds);
        summary.setShutdownDurationS((int) shutdown.durationSeconds);
        summary.setMissingDataS((int) missing.durationSeconds);

        summary.setWorkingRatio(working.ratio);
        summary.setStandbyRatio(standby.ratio);
        summary.setFaultRatio(fault.ratio);
        summary.setShutdownRatio(shutdown.ratio);

        // 计算数据完整度
        if (shiftRange != null) {
            long shiftDurationSeconds = (shiftRange.getEndTs() - shiftRange.getStartTs()) / 1000;
            if (shiftDurationSeconds > 0) {
                long totalRecordedDuration = working.durationSeconds + standby.durationSeconds
                        + fault.durationSeconds + shutdown.durationSeconds;
                BigDecimal completeness = BigDecimal.valueOf(totalRecordedDuration)
                        .divide(BigDecimal.valueOf(shiftDurationSeconds), 4, RoundingMode.HALF_UP);
                summary.setDataCompleteness(completeness);
            }
        }

        // 构建状态统计JSON
        Map<String, Object> stateStatisticsJson = new HashMap<>();
        for (Map.Entry<String, StateStatistics> entry : stateStats.entrySet()) {
            Map<String, Object> stateInfo = new HashMap<>();
            stateInfo.put("durationSeconds", entry.getValue().durationSeconds);
            stateInfo.put("ratio", entry.getValue().ratio);
            stateInfo.put("fragmentCount", entry.getValue().fragmentCount);
            stateStatisticsJson.put(entry.getKey(), stateInfo);
        }
        summary.setStateStatistics(stateStatisticsJson);

        // 保存或更新
        if (exists) {
            stateSummaryRepository.update(summary);
        } else {
            stateSummaryRepository.insert(summary);
        }
    }

    /**
     * 重新计算单条汇总记录（供补偿任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean recalculateSummary(DeviceStateSummaryDO summary) {
        // 查询设备信息
        Optional<DeviceInfoDO> deviceOpt = deviceInfoRepository.findById(summary.getDeviceInfoId());
        if (deviceOpt.isEmpty() || Boolean.TRUE.equals(deviceOpt.get().getDeleted())) {
            log.warn("设备不存在或已删除: deviceId={}", summary.getDeviceInfoId());
            return false;
        }
        DeviceInfoDO device = deviceOpt.get();

        long shiftStartTs = summary.getShiftStartTs() * 1000L;

        // 查询班次配置（无配置则使用默认配置）
        DeviceShiftConfigDO shiftConfig = shiftConfigService.getCurrentConfiguration(
                device.getOrgFactoryId(), summary.getDeviceInfoId(), shiftStartTs);

        // 计算班次时间范围
        ShiftTimeRange shiftRange = shiftCalculationService.calculateShiftRange(
                device.getOrgFactoryId(),
                summary.getDeviceInfoId(),
                shiftStartTs);

        if (shiftRange == null) {
            log.warn("无法计算班次时间范围: deviceId={}, shiftDate={}, shiftCode={}",
                    summary.getDeviceInfoId(), summary.getSummaryDate(), summary.getShiftCode());
            return false;
        }

        // 查询班次内的状态记录
        List<DeviceStateRecordDO> stateRecords =
                stateRecordRepository.selectByRange(
                        summary.getDeviceInfoId(),
                        shiftRange.getStartTs() / 1000,
                        shiftRange.getEndTs() / 1000);

        // 重新计算状态统计
        Map<String, StateStatistics> stateStats =
                stateStatisticsService.calculateStatistics(
                        stateRecords,
                        shiftRange.getStartTs() / 1000,
                        shiftRange.getEndTs() / 1000);

        // 强制更新汇总记录（补偿任务需要强制更新，即使已统计过）
        forceUpdateSummary(
                summary.getDeviceInfoId(),
                device.getOrgFactoryId(),
                summary.getSummaryDate(),
                shiftRange,
                stateStats);

        return true;
    }
}

