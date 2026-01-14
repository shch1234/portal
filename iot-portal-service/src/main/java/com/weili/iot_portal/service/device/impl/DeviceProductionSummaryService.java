package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionSummaryRepository;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
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
import java.util.*;
import java.util.stream.Collectors;

import static com.weili.iot_portal.service.device.util.DeviceLogContext.*;

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

    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(long statisticsTimeSeconds,
                                                              int batchSize,
                                                              long timeoutMillis,
                                                              int lookbackDays) {
        // 计算时间范围：从当前时间往前推N天（秒）
        long startTsSeconds = statisticsTimeSeconds - (lookbackDays * 24L * 3600L);

        log.info("产量汇总: 处理时间范围 {} 天，开始时间戳(秒)={}, 结束时间戳(秒)={}",
                lookbackDays, startTsSeconds, statisticsTimeSeconds);

        // 优化1：只查询有产量记录数据的设备ID（避免查询所有设备）
        List<Long> deviceIdsWithData = productionRecordRepository.findDistinctDeviceIdsWithProductionRecords(
                startTsSeconds, statisticsTimeSeconds);
        if (deviceIdsWithData == null || deviceIdsWithData.isEmpty()) {
            log.info("产量汇总: 未发现有待处理的产量记录数据");
            return BatchProcessResult.completed(0, 0, 0);
        }

        log.info("产量汇总: 发现 {} 台设备有待处理的产量记录数据", deviceIdsWithData.size());

        // 批量查询设备信息，并按工厂分组
        List<DeviceInfoDO> devices = deviceInfoRepository.selectByIds(deviceIdsWithData);
        Map<Long, DeviceInfoDO> deviceMap = devices.stream()
                .collect(Collectors.toMap(DeviceInfoDO::getId, d -> d));

        Map<Long, List<DeviceInfoDO>> devicesByFactory = new HashMap<>();
        int notFoundCount = 0;
        for (Long deviceId : deviceIdsWithData) {
            DeviceInfoDO device = deviceMap.get(deviceId);
            if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
                notFoundCount++;
                continue;
            }
            // 只处理监控中、在用状态、且关联工厂的设备
            if (!isDeviceValidForProcessing(device)) {
                notFoundCount++;
                continue;
            }
            devicesByFactory.computeIfAbsent(device.getOrgFactoryId(), k -> new ArrayList<>()).add(device);
        }

        if (notFoundCount > 0) {
            log.warn("产量汇总: 有 {} 个设备ID在 device_info 中未找到或已删除或不符合条件", notFoundCount);
        }

        if (devicesByFactory.isEmpty()) {
            log.info("产量汇总: 没有符合条件的设备需要处理");
            return BatchProcessResult.completed(0, notFoundCount, 0);
        }

        int success = 0, skip = 0, error = 0;

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : devicesByFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> factoryDevices = entry.getValue();
            try {
                BatchProcessResult factoryResult = processFactoryDevicesWithCheckpoint(
                        factoryId, factoryDevices, statisticsTimeSeconds, batchSize, timeoutMillis, startTsSeconds);
                success += factoryResult.getSuccessCount();
                skip += factoryResult.getSkipCount();
                error += factoryResult.getErrorCount();
            } catch (Exception e) {
                error += factoryDevices.size();
                log.error("处理工厂失败: factoryId={}, deviceCount={}", factoryId, factoryDevices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip + notFoundCount, error);
    }

    /**
     * 检查设备是否有效，可用于处理
     * <p>
     * 统一设备过滤条件（与其他服务保持一致）：
     * 1. 必须监控中 (isMonitored = true)
     * 2. 必须是在用状态 (deviceStatus = 'ACTIVE')
     * 3. 必须关联工厂 (orgFactoryId != null)
     *
     * @param device 设备信息
     * @return true 如果设备有效，false 如果设备无效
     */
    private boolean isDeviceValidForProcessing(DeviceInfoDO device) {
        return Boolean.TRUE.equals(device.getIsMonitored())
                && DEVICE_STATUS_ACTIVE.equals(device.getDeviceStatus())
                && device.getOrgFactoryId() != null;
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(Long factoryId,
                                                                  List<DeviceInfoDO> devices,
                                                                  long statisticsTimeSeconds,
                                                                  int batchSize,
                                                                  long timeoutMillis) {
        // 默认时间范围：7天（用于向后兼容）
        long startTsSeconds = statisticsTimeSeconds - (7 * 24L * 3600L);
        return processFactoryDevicesWithCheckpoint(factoryId, devices, statisticsTimeSeconds, batchSize, timeoutMillis, startTsSeconds);
    }

    /**
     * 处理指定工厂的设备（带检查点，内部方法）
     *
     * @param factoryId 工厂ID
     * @param devices 设备列表
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @param batchSize 每批处理设备数
     * @param timeoutMillis 超时时间（毫秒）
     * @param startTsSeconds 开始时间戳（秒），只处理班次结束时间 >= startTsSeconds 的班次
     */
    private BatchProcessResult processFactoryDevicesWithCheckpoint(Long factoryId,
                                                                   List<DeviceInfoDO> devices,
                                                                   long statisticsTimeSeconds,
                                                                   int batchSize,
                                                                   long timeoutMillis,
                                                                   long startTsSeconds) {

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
            for (DeviceInfoDO device : batch) {
                try {
                    int processedCount = processSingleDevice(device, statisticsTimeSeconds, startTsSeconds);
                    if (processedCount > 0) {
                        success++;
                    } else {
                        skip++;
                    }
                    processedIds.add(device.getId());
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
     * <p>
     * 处理时间范围内的所有已结束的班次，从当前班次开始往前遍历
     *
     * @param device 设备信息
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @param startTsSeconds 开始时间戳（秒），只处理班次结束时间 >= startTsSeconds 的班次
     * @return 处理的班次数量，如果处理失败则返回0
     */
    protected int processSingleDevice(DeviceInfoDO device, long statisticsTimeSeconds, long startTsSeconds) {
        // 设置设备编号到 MDC，使日志能够显示设备编号
        setDeviceCode(device);

        try {
            long statisticsTimeMs = statisticsTimeSeconds * 1000;
            long startTsMs = startTsSeconds * 1000;

            int processedCount = 0;
            ShiftTimeRange currentRange = shiftCalculationService.calculateShiftRange(
                    device.getOrgFactoryId(), device.getId(), statisticsTimeMs);

            // 从当前班次开始，往前遍历所有已结束的班次
            ShiftTimeRange range = currentRange;

            // 如果当前班次正在进行中，先获取上一个班次
            if (range != null && range.getEndTs() != null && range.getEndTs() > statisticsTimeMs) {
                // 当前班次尚未结束，跳过当前班次，从上一个班次开始处理
                log.debug("产量汇总: 当前班次正在进行中，跳过: deviceId={}, shiftEndTs={}, statisticsTimeMs={}",
                        device.getId(), range.getEndTs(), statisticsTimeMs);

                if (range.getStartTs() > startTsMs) {
                    // 如果当前班次开始时间在时间范围内，尝试获取上一个班次
                    ShiftTimeRange previousRange = shiftCalculationService.calculatePreviousShiftRange(
                            device.getOrgFactoryId(), device.getId(), range.getStartTs());
                    if (previousRange != null && previousRange.getEndTs() != null) {
                        range = previousRange;
                    } else {
                        // 无法获取上一个班次，无法处理
                        return 0;
                    }
                } else {
                    // 当前班次开始时间早于时间范围开始时间，无法处理
                    log.debug("产量汇总: 当前班次开始时间早于时间范围: deviceId={}, shiftStartTs={}, startTsMs={}",
                            device.getId(), range.getStartTs(), startTsMs);
                    return 0;
                }
            }

            while (range != null && range.getEndTs() != null) {
                // 只处理已结束的班次
                if (range.getEndTs() > statisticsTimeMs) {
                    // 班次尚未结束，跳过并继续获取上一个班次
                    log.debug("产量汇总: 跳过正在进行中的班次: deviceId={}, shiftEndTs={}, statisticsTimeMs={}",
                            device.getId(), range.getEndTs(), statisticsTimeMs);

                    // 获取上一个班次
                    if (range.getStartTs() > startTsMs) {
                        ShiftTimeRange previousRange = shiftCalculationService.calculatePreviousShiftRange(
                                device.getOrgFactoryId(), device.getId(), range.getStartTs());
                        if (previousRange != null && previousRange.getEndTs() != null) {
                            range = previousRange;
                            continue;
                        }
                    }
                    break;
                }

                // 只处理时间范围内的班次
                if (range.getEndTs() < startTsMs) {
                    // 班次结束时间早于开始时间，停止遍历
                    log.debug("产量汇总: 班次结束时间早于时间范围: deviceId={}, shiftEndTs={}, startTsMs={}",
                            device.getId(), range.getEndTs(), startTsMs);
                    break;
                }

                long shiftStartSec = range.getStartTs() / 1000;
                long shiftEndSec = range.getEndTs() / 1000;
                if (shiftEndSec <= shiftStartSec) {
                    log.warn("产量汇总: 班次时间范围无效: deviceId={}, shiftStartSec={}, shiftEndSec={}",
                            device.getId(), shiftStartSec, shiftEndSec);
                    break;
                }

                long count = productionRecordRepository.countCompletedInRange(device.getId(), shiftStartSec, shiftEndSec);

                log.debug("产量汇总: 处理班次: deviceId={}, shiftDate={}, shiftCode={}, shiftStartSec={}, shiftEndSec={}, count={}",
                        device.getId(), Instant.ofEpochMilli(range.getStartTs()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        range.getShiftCode(), shiftStartSec, shiftEndSec, count);

                LocalDate shiftDate = Instant.ofEpochMilli(range.getStartTs())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                upsertSummary(device.getId(), shiftDate, range.getShiftCode(), shiftStartSec, shiftEndSec, count, statisticsTimeSeconds);
                processedCount++;

                // 获取上一个班次
                if (range.getStartTs() <= startTsMs) {
                    // 已经处理到时间范围开始时间，停止遍历
                    log.debug("产量汇总: 已处理到时间范围开始时间: deviceId={}, shiftStartTs={}, startTsMs={}",
                            device.getId(), range.getStartTs(), startTsMs);
                    break;
                }

                ShiftTimeRange previousRange = shiftCalculationService.calculatePreviousShiftRange(
                        device.getOrgFactoryId(), device.getId(), range.getStartTs());
                if (previousRange == null || previousRange.getEndTs() == null) {
                    log.debug("产量汇总: 无法获取上一个班次，停止遍历: deviceId={}", device.getId());
                    break;
                }

                // 检查是否已经处理过这个班次（避免重复处理）
                // 如果上一个班次的结束时间 >= 当前班次的开始时间，说明班次有重叠或顺序错误，停止遍历
                if (previousRange.getEndTs() >= range.getStartTs()) {
                    log.warn("产量汇总: 班次时间范围异常，停止遍历: deviceId={}, previousShiftEndTs={}, currentShiftStartTs={}",
                            device.getId(), previousRange.getEndTs(), range.getStartTs());
                    break;
                }

                range = previousRange;
            }

            return processedCount;
        } finally {
            // 清除设备编号 MDC，避免线程复用导致设备编号污染
            clearDeviceCode();
        }
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

}

