package com.weili.iot_portal.task.devicemng;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceMetricsShiftDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceParameterDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceMetricsShiftMapper;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceParameterRepository;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateSummaryRepository;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private final DeviceBaseInfoMapper deviceBaseInfoMapper;
    private final DeviceStateSummaryRepository deviceStateSummaryRepository;
    private final DeviceParameterRepository deviceParameterRepository;
    private final DeviceMetricsShiftMapper deviceMetricsShiftMapper;
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

        List<DeviceBaseInfoDO> devices = queryAllDevices();
        if (devices.isEmpty()) {
            return JobExecutionResult.empty();
        }

        int success = 0, skip = 0, error = 0;
        Map<String, List<DeviceBaseInfoDO>> grouped = devices.stream()
                .filter(d -> StringUtils.isNotBlank(d.getOrgFactoryId()))
                .collect(Collectors.groupingBy(DeviceBaseInfoDO::getTenantUuid));

        for (Map.Entry<String, List<DeviceBaseInfoDO>> tenantEntry : grouped.entrySet()) {
            String tenantId = tenantEntry.getKey();
            for (DeviceBaseInfoDO device : tenantEntry.getValue()) {
                try {
                    boolean processed = processDevice(tenantId, device, statPoint);
                    if (processed) success++; else skip++;
                } catch (Exception e) {
                    error++;
                    log.error("指标汇总失败 deviceId={}", device.getId(), e);
                }
            }
        }

        return JobExecutionResult.of(success, skip, error);
    }

    private List<DeviceBaseInfoDO> queryAllDevices() {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceBaseInfoDO::getDeleted, false);
        return deviceBaseInfoMapper.selectList(wrapper);
    }

    /**
     * 处理单台设备在统计时间点前已结束并已汇总的班次
     */
    private boolean processDevice(String tenantId, DeviceBaseInfoDO device, long statPointSec) {
        // 找到已结束且 finalized 的状态汇总（shift_end_ts <= statPointSec 且 is_finalized=1）
        List<DeviceStateSummaryDO> summaries = deviceStateSummaryRepository.selectByRange(
                tenantId, device.getId(), null, statPointSec);
        if (summaries == null || summaries.isEmpty()) {
            return false;
        }

        int processed = 0;
        for (DeviceStateSummaryDO summary : summaries) {
            if (!Boolean.TRUE.equals(summary.getIsFinalized())) {
                continue; // 状态汇总未完成，跳过等待下次
            }
            upsertMetrics(tenantId, summary, device);
            processed++;
        }
        return processed > 0;
    }

    private void upsertMetrics(String tenantId, DeviceStateSummaryDO stateSummary, DeviceBaseInfoDO device) {
        long shiftStart = stateSummary.getShiftStartTs();
        long shiftEnd = stateSummary.getShiftEndTs();
        if (shiftStart <= 0 || shiftEnd <= 0 || shiftEnd <= shiftStart) {
            return;
        }

        long plannedDowntime = getPlannedDowntimeSeconds(tenantId, device.getId());
        long shiftDuration = shiftEnd - shiftStart;
        long plannedRuntime = Math.max(0, shiftDuration - plannedDowntime);

        long standby = getSafe(stateSummary.getStandbyDurationS());
        long fault = getSafe(stateSummary.getFaultDurationS());
        long shutdown = getSafe(stateSummary.getShutdownDurationS());
        long working = getSafe(stateSummary.getWorkingDurationS());
        long unplanned = standby + fault + shutdown;
        long actualRuntime = Math.max(0, plannedRuntime - unplanned);

        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(tenantId, device.getId(), shiftStart, shiftEnd);
        long theoreticalCycle = getTheoreticalCycleSeconds(tenantId, device.getId());

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

        LambdaQueryWrapper<DeviceMetricsShiftDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMetricsShiftDO::getTenantUuid, tenantId)
                .eq(DeviceMetricsShiftDO::getDeviceInfoId, device.getId())
                .eq(DeviceMetricsShiftDO::getShiftDate, stateSummary.getSummaryDate())
                .eq(DeviceMetricsShiftDO::getShiftCode, stateSummary.getShiftCode());
        DeviceMetricsShiftDO existing = deviceMetricsShiftMapper.selectOne(wrapper);
        if (existing == null) {
            DeviceMetricsShiftDO record = new DeviceMetricsShiftDO();
            record.setId(IdWorker.getIdStr());
            record.setTenantUuid(tenantId);
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
            deviceMetricsShiftMapper.insert(record);
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
            deviceMetricsShiftMapper.updateById(existing);
        }
    }

    private long getPlannedDowntimeSeconds(String tenantId, String deviceId) {
        List<DeviceParameterDO> params = deviceParameterRepository.selectCurrent(tenantId, deviceId);
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParameterDO::getParameterValue)
                .map(v -> v.longValue())
                .orElse(0L);
    }

    private long getTheoreticalCycleSeconds(String tenantId, String deviceId) {
        List<DeviceParameterDO> params = deviceParameterRepository.selectCurrent(tenantId, deviceId);
        return params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParameterDO::getParameterValue)
                .map(v -> v.longValue())
                .orElse(0L);
    }

    private long getSafe(Integer v) {
        return v == null ? 0L : v;
    }
}


