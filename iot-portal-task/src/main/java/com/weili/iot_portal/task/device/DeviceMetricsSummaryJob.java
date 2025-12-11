package com.weili.iot_portal.task.device;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.*;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备班次指标汇总任务
 * 依赖已完成的 device_state_summary（is_finalized=1），计算时间开动率等指标，写入 device_metrics_summary
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMetricsSummaryJob extends BaseScheduledJob {

    @Value("${shift.summary.delay-minutes:5}")
    private int delayMinutes;

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateSummaryRepository deviceStateSummaryRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceMetricSummaryRepository deviceMetricSummaryRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;

    private static final String PARAM_PLANNED_DOWNTIME = "PLANNED_DOWNTIME";
    private static final String PARAM_THEORETICAL_CYCLE = "THEORETICAL_CYCLE";
    private static final String CALC_SOURCE = "SCHEDULED";

    @Override
    protected String getJobName() {
        return "设备班次指标汇总任务";
    }

    @Override
    @XxlJob("deviceMetricsSummaryJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        long nowSec = System.currentTimeMillis() / 1000;
        long statPoint = nowSec - delayMinutes * 60L;
        XxlJobHelper.log("指标汇总统计时间点: {}, 延迟: {} 分钟", Instant.ofEpochSecond(statPoint), delayMinutes);

        List<DeviceInfoDO> devices = queryAllDevices();
        if (devices.isEmpty()) {
            return JobExecutionResult.empty();
        }

        int success = 0, skip = 0, error = 0;
        Map<String, List<DeviceInfoDO>> grouped = devices.stream()
                .filter(d -> StringUtils.isNotBlank(d.getOrgFactoryId()))
                .collect(Collectors.groupingBy(DeviceInfoDO::getTenantUuid));

        for (Map.Entry<String, List<DeviceInfoDO>> tenantEntry : grouped.entrySet()) {
            for (DeviceInfoDO device : tenantEntry.getValue()) {
                try {
                    boolean processed = processDevice(device, statPoint);
                    if (processed) success++;
                    else skip++;
                } catch (Exception e) {
                    error++;
                    log.error("指标汇总失败 deviceId={}", device.getId(), e);
                }
            }
        }

        return JobExecutionResult.of(success, skip, error);
    }

    private List<DeviceInfoDO> queryAllDevices() {
        return deviceInfoRepository.findAllActive();
    }

    /**
     * 处理单台设备在统计时间点前已结束并已汇总的班次
     */
    private boolean processDevice(DeviceInfoDO device, long statPointSec) {
        // 找到已结束且 finalized 的状态汇总（shift_end_ts <= statPointSec 且 is_finalized=1）
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository.selectByRange(
                device.getId(), null, statPointSec);
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }

        int processed = 0;
        for (DeviceStateSummaryDO summary : summaries) {
            if (!Boolean.TRUE.equals(summary.getIsFinalized())) {
                continue; // 状态汇总未完成，跳过等待下次
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

        BigDecimal oee = (uptimeRate == null || performanceRate == null)
                ? BigDecimal.ZERO
                : uptimeRate.multiply(performanceRate)
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
            record.setId(IdWorker.getIdStr());
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

    private long getPlannedDowntimeSeconds(String deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(v -> v.longValue())
                .orElse(0L);
    }

    private long getTheoreticalCycleSeconds(String deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(v -> v.longValue())
                .orElse(0L);
    }

    private long getSafe(Integer v) {
        return v == null ? 0L : v;
    }
}


