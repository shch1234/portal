package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.*;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.CheckpointData;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备班次指标汇总服务实现
 */
@Slf4j
@Service
public class DeviceMetricsSummaryService implements IDeviceMetricsSummaryService {

    private static final String PARAM_PLANNED_DOWNTIME = "PLANNED_DOWNTIME";
    private static final String PARAM_THEORETICAL_CYCLE = "THEORETICAL_CYCLE";
    private static final String CALC_SOURCE = "SCHEDULED";

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateSummaryRepository deviceStateSummaryRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final ICheckpointService<CheckpointData> checkpointService;

    public DeviceMetricsSummaryService(DeviceInfoRepository deviceInfoRepository,
                                       DeviceStateSummaryRepository deviceStateSummaryRepository,
                                       DeviceParamConfigRepository deviceParamConfigRepository,
                                       DeviceMetricSummaryRepository deviceMetricSummaryRepository,
                                       DeviceProductionRecordRepository deviceProductionRecordRepository,
                                       @Qualifier("deviceMetricsSummaryCheckpointService")
                                       ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.deviceStateSummaryRepository = deviceStateSummaryRepository;
        this.deviceParamConfigRepository = deviceParamConfigRepository;
        this.deviceMetricSummaryRepository = deviceMetricSummaryRepository;
        this.deviceProductionRecordRepository = deviceProductionRecordRepository;
        this.checkpointService = checkpointService;
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(long statPointSeconds, int batchSize, long timeoutMillis) {
        List<DeviceInfoDO> devices = deviceInfoRepository.findAllActive();
        if (devices == null || devices.isEmpty()) {
            return BatchProcessResult.completed(0, 0, 0);
        }

        int success = 0, skip = 0, error = 0;

        // 过滤并按工厂分组
        Map<Long, List<DeviceInfoDO>> devicesByFactory = devices.stream()
                .filter(d -> d.getOrgFactoryId()!=null)
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        if (devices.size() > filtered) {
            skip += (int) (devices.size() - filtered);
            log.warn("指标汇总: 有 {} 个设备未关联工厂，已跳过", devices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> entry : devicesByFactory.entrySet()) {
            Long factoryId = entry.getKey();
            List<DeviceInfoDO> factoryDevices = entry.getValue();
            try {
                BatchProcessResult factoryResult = processFactoryDevicesWithCheckpoint(
                        factoryId, factoryDevices, statPointSeconds, batchSize, timeoutMillis);
                success += factoryResult.getSuccessCount();
                skip += factoryResult.getSkipCount();
                error += factoryResult.getErrorCount();
            } catch (Exception e) {
                error += factoryDevices.size();
                log.error("处理工厂失败: factoryId={}, deviceCount={}", factoryId, factoryDevices.size(), e);
            }
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    @Override
    public BatchProcessResult processFactoryDevicesWithCheckpoint(Long factoryId,
                                                                  List<DeviceInfoDO> devices,
                                                                  long statPointSeconds,
                                                                  int batchSize,
                                                                  long timeoutMillis) {
        // 加载检查点
        Set<Long> processedIds = checkpointService.getProcessedDeviceIds(factoryId, statPointSeconds);
        List<DeviceInfoDO> remaining = devices.stream()
                .filter(d -> !processedIds.contains(d.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) {
            checkpointService.clearCheckpoint(factoryId, statPointSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        if (!processedIds.isEmpty()) {
            log.info("指标汇总从检查点恢复: factoryId={}, 已处理={}, 剩余={}", factoryId, processedIds.size(), remaining.size());
        }

        int success = 0, skip = 0, error = 0;
        List<Long> newProcessed = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 0; i < remaining.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, remaining.size());
            List<DeviceInfoDO> batch = remaining.subList(i, endIndex);

            for (DeviceInfoDO device : batch) {
                try {
                    boolean processed = processDevice(device, statPointSeconds);
                    if (processed) {
                        success++;
                    } else {
                        skip++;
                    }
                    processedIds.add(device.getId());
                    newProcessed.add(device.getId());
                } catch (Exception e) {
                    error++;
                    log.error("指标汇总失败 deviceId={}", device.getId(), e);
                }
            }

            // 保存检查点
            checkpointService.saveCheckpoint(factoryId, statPointSeconds, new ArrayList<>(processedIds));

            // 超时检查
            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("指标汇总超时: factoryId={}, processed={}, remaining={}",
                        factoryId, processedIds.size(), devices.size() - processedIds.size());
                return BatchProcessResult.incomplete(success, skip, error);
            }
        }

        // 全部完成，清除检查点
        checkpointService.clearCheckpoint(factoryId, statPointSeconds);
        return BatchProcessResult.completed(success, skip, error);
    }

    /**
     * 处理单台设备在统计时间点前已结束并已汇总的班次
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean processDevice(DeviceInfoDO device, long statPointSec) {
        // 找到已结束且 finalized 的状态汇总（shift_end_ts <= statPointSec 且 is_finalized=1）
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository.selectByRange(
                device.getId(), null, statPointSec);
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }

        int processed = 0;
        for (DeviceStateSummaryDO summary : summaries) {
            if (!Boolean.TRUE.equals(summary.getIsFinalized())) {
                continue; // 状态汇总未完成，跳过
            }
            upsertMetrics(summary, device);
            processed++;
        }
        return processed > 0;
    }

    private void upsertMetrics(DeviceStateSummaryDO stateSummary, DeviceInfoDO device) {
        long shiftStart = stateSummary.getShiftStartTs();
        long shiftEnd = stateSummary.getShiftEndTs();
        if (shiftStart <= 0 || shiftEnd <= 0 || shiftEnd <= shiftStart) {
            return;
        }

        long plannedDowntime = getPlannedDowntimeSeconds(device.getId());
        long shiftDuration = shiftEnd - shiftStart;
        long plannedRuntime = Math.max(0, shiftDuration - plannedDowntime);

        long standby = getSafe(stateSummary.getStandbyDurationS());
        long fault = getSafe(stateSummary.getFaultDurationS());
        long shutdown = getSafe(stateSummary.getShutdownDurationS());
        long working = getSafe(stateSummary.getWorkingDurationS());
        long unplanned = standby + fault + shutdown;
        long actualRuntime = Math.max(0, plannedRuntime - unplanned);

        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(device.getId(), shiftStart, shiftEnd);
        long theoreticalCycle = getTheoreticalCycleSeconds(device.getId());

        BigDecimal uptimeRate = plannedRuntime == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(actualRuntime)
                .divide(BigDecimal.valueOf(plannedRuntime), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal performanceRate = (actualRuntime == 0 || theoreticalCycle <= 0 || actualOutput <= 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(actualOutput)
                .multiply(BigDecimal.valueOf(theoreticalCycle))
                .divide(BigDecimal.valueOf(actualRuntime), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal availabilityRate = shiftDuration == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(working)
                .divide(BigDecimal.valueOf(shiftDuration), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal faultRate = plannedRuntime == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(fault)
                .divide(BigDecimal.valueOf(plannedRuntime), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal oee = uptimeRate.multiply(performanceRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP); // 假设合格品率=100%

        Map<String, Object> metrics = Map.of(
                "uptimeRate", uptimeRate,
                "performanceRate", performanceRate,
                "availabilityRate", availabilityRate,
                "faultRate", faultRate,
                "oee", oee
        );
        Map<String, Object> calcData = new HashMap<>();
        calcData.put("shiftDuration", shiftDuration);
        calcData.put("plannedDowntime", plannedDowntime);
        calcData.put("plannedRuntime", plannedRuntime);
        calcData.put("standby", standby);
        calcData.put("fault", fault);
        calcData.put("shutdown", shutdown);
        calcData.put("actualRuntime", actualRuntime);
        calcData.put("actualOutput", actualOutput);
        calcData.put("theoreticalCycle", theoreticalCycle);
        calcData.put("working", working);
        calcData.put("faultDuration", fault);

        DeviceMetricSummaryDO existing = deviceMetricSummaryRepository.findByShift(
                device.getId(), stateSummary.getSummaryDate(), stateSummary.getShiftCode());
        if (existing == null) {
            DeviceMetricSummaryDO record = new DeviceMetricSummaryDO();
            record.setDeviceInfoId(device.getId());
            record.setShiftDate(stateSummary.getSummaryDate());
            record.setShiftCode(stateSummary.getShiftCode());
            record.setShiftStartTs(stateSummary.getShiftStartTs());
            record.setShiftEndTs(stateSummary.getShiftEndTs());
            record.setMetrics(metrics);
            record.setCalculationData(calcData);
            record.setParameterSnapshot(Map.of(
                    PARAM_PLANNED_DOWNTIME, plannedDowntime,
                    PARAM_THEORETICAL_CYCLE, theoreticalCycle
            ));
            record.setIsFinalized(true);
            record.setCalculationStatus("CALCULATED");
            record.setCalculatedTime(System.currentTimeMillis() / 1000);
            record.setCalculationSource(CALC_SOURCE);
            deviceMetricSummaryRepository.insert(record);
        } else {
            existing.setShiftStartTs(stateSummary.getShiftStartTs());
            existing.setShiftEndTs(stateSummary.getShiftEndTs());
            existing.setMetrics(metrics);
            existing.setCalculationData(calcData);
            existing.setParameterSnapshot(Map.of(
                    PARAM_PLANNED_DOWNTIME, plannedDowntime,
                    PARAM_THEORETICAL_CYCLE, theoreticalCycle
            ));
            existing.setIsFinalized(true);
            existing.setCalculationStatus("CALCULATED");
            existing.setCalculatedTime(System.currentTimeMillis() / 1000);
            existing.setCalculationSource(CALC_SOURCE);
            deviceMetricSummaryRepository.update(existing);
        }
    }

    private long getPlannedDowntimeSeconds(Long deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(Number::longValue)
                .orElse(0L);
    }

    private long getTheoreticalCycleSeconds(Long deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(Number::longValue)
                .orElse(0L);
    }

    private long getSafe(Integer v) {
        return v == null ? 0L : v;
    }
}

