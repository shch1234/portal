package com.weili.iot_portal.task.devicemng;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceParameterDO;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceParameterRepository;
import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备实时指标计算任务（每5分钟刷新一次）
 * 当前实现：时间开动率
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMetricsJob extends BaseScheduledJob {

    @Value("${rt.metrics.refresh-seconds:300}")
    private long refreshSeconds;

    @Value("${rt.metrics.ttl-seconds:600}")
    private long ttlSeconds;

    private final DeviceBaseInfoMapper deviceBaseInfoMapper;
    private final DeviceStateTimelineRepository deviceStateTimelineRepository;
    private final DeviceParameterRepository deviceParameterRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final ShiftConfigurationService shiftConfigurationService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PARAM_PLANNED_DOWNTIME = "PLANNED_DOWNTIME";
    private static final String PARAM_THEORETICAL_CYCLE = "THEORETICAL_CYCLE";

    @Override
    protected String getJobName() {
        return "设备实时指标计算任务";
    }

    @Override
    @XxlJob("deviceMetricsJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        List<DeviceBaseInfoDO> allDevices = queryAllDevices();
        if (allDevices.isEmpty()) {
            return JobExecutionResult.empty();
        }
        int success = 0, error = 0;

        for (DeviceBaseInfoDO device : allDevices) {
            try {
                processDevice(device);
                success++;
            } catch (Exception e) {
                error++;
                log.error("计算指标失败 deviceId={}", device.getId(), e);
            }
        }
        return JobExecutionResult.of(success, 0, error);
    }

    private List<DeviceBaseInfoDO> queryAllDevices() {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceBaseInfoDO::getDeleted, false)
                .isNotNull(DeviceBaseInfoDO::getOrgFactoryId);
        return deviceBaseInfoMapper.selectList(wrapper);
    }

    private void processDevice(DeviceBaseInfoDO device) {
        String tenantId = device.getTenantUuid();
        String factoryId = device.getOrgFactoryId();
        String deviceId = device.getId();

        long nowMs = System.currentTimeMillis();
        ShiftTimeRange shift = shiftConfigurationService.calculateShiftRange(tenantId, factoryId, deviceId, nowMs);
        if (shift == null || shift.getStartTs() == null) {
            return;
        }
        long shiftStartSec = shift.getStartTs() / 1000;
        long shiftEndSec = (shift.getEndTs() != null ? shift.getEndTs() : nowMs) / 1000;

        long plannedDowntime = getPlannedDowntimeSeconds(tenantId, deviceId);
        long shiftDuration = Math.max(0, shiftEndSec - shiftStartSec);
        long plannedRuntime = Math.max(0, shiftDuration - plannedDowntime);

        Map<String, Long> stateDurations = sumStateDurations(tenantId, deviceId, shiftStartSec, shiftEndSec, nowMs / 1000);
        long workingDuration = stateDurations.getOrDefault("WORKING", 0L);
        long faultDuration = stateDurations.getOrDefault("FAULT", 0L);
        long unplannedDowntime = stateDurations.getOrDefault("STANDBY", 0L)
                + faultDuration
                + stateDurations.getOrDefault("SHUTDOWN", 0L);
        long actualRuntime = Math.max(0, plannedRuntime - unplannedDowntime);

        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(tenantId, deviceId, shiftStartSec, shiftEndSec);
        long theoreticalCycle = getTheoreticalCycleSeconds(tenantId, deviceId);

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

        long elapsedCalendar = Math.max(1, (nowMs / 1000) - shiftStartSec);
        BigDecimal availabilityRate = BigDecimal.valueOf(workingDuration)
                .divide(BigDecimal.valueOf(elapsedCalendar), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 实时 OEE：合格品率默认 100%
        BigDecimal faultRate = plannedRuntime == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(faultDuration)
                .divide(BigDecimal.valueOf(plannedRuntime), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal oee = uptimeRate
                .multiply(performanceRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);

        writeMetricToRedis(tenantId, factoryId, deviceId, uptimeRate, performanceRate, availabilityRate, faultRate, oee, nowMs / 1000);
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

    private Map<String, Long> sumStateDurations(String tenantId, String deviceId, long startSec, long endSec, long nowSec) {
        List<DeviceStateTimelineDO> timelines = deviceStateTimelineRepository.selectByRange(tenantId, deviceId, startSec, endSec);
        Map<String, Long> result = new HashMap<>();
        for (DeviceStateTimelineDO t : timelines) {
            String state = t.getStateCode();
            if (StringUtils.isBlank(state)) {
                continue;
            }
            long segStart = Math.max(startSec, t.getStartTs());
            long segEnd = Math.min(endSec, t.getEndTs() != null ? t.getEndTs() : nowSec);
            if (segEnd > segStart) {
                long dur = segEnd - segStart;
                result.merge(state.toUpperCase(), dur, Long::sum);
            }
        }
        return result;
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

    private void writeMetricToRedis(String tenantId, String factoryId, String deviceId,
                                    BigDecimal uptimeRate, BigDecimal performanceRate,
                                    BigDecimal availabilityRate, BigDecimal faultRate,
                                    BigDecimal oee, long updatedAtSec) {
        String key = String.format(RedisConstant.RT_METRIC, defaultBlank(tenantId), defaultBlank(factoryId), defaultBlank(deviceId));
        Map<String, String> payload = new HashMap<>();
        payload.put("metric.uptimeRate", uptimeRate.toPlainString());
        payload.put("metric.performanceRate", performanceRate.toPlainString());
        payload.put("metric.availabilityRate", availabilityRate.toPlainString());
        payload.put("metric.faultRate", faultRate.toPlainString());
        payload.put("metric.oee", oee.toPlainString());
        payload.put("updatedAt", String.valueOf(updatedAtSec));
        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
    }

    private String defaultBlank(String v) {
        return StringUtils.defaultIfBlank(v, "none");
    }
}


