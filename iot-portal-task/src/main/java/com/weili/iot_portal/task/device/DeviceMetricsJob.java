package com.weili.iot_portal.task.device;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceParamConfigRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备实时指标计算任务（每5分钟刷新一次）
 * 当前实现：时间开动率
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceMetricsJob extends BaseScheduledJob {
    @Value("${rt.metrics.ttl-seconds:600}")
    private long ttlSeconds;

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository deviceStateRecordRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
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
        List<DeviceInfoDO> allDevices = queryAllDevices();
        if (allDevices.isEmpty()) {
            return JobExecutionResult.empty();
        }
        int success = 0, error = 0;

        for (DeviceInfoDO device : allDevices) {
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

    private List<DeviceInfoDO> queryAllDevices() {
        return deviceInfoRepository.findActiveWithFactory();
    }

    private void processDevice(DeviceInfoDO device) {
        String factoryId = device.getOrgFactoryId();
        String deviceId = device.getId();

        long nowMs = System.currentTimeMillis();
        ShiftTimeRange shift = shiftCalculationService.calculateShiftRange(factoryId, deviceId, nowMs);
        if (shift == null || shift.getStartTs() == null) {
            return;
        }
        long shiftStartSec = shift.getStartTs() / 1000;
        long shiftEndSec = (shift.getEndTs() != null ? shift.getEndTs() : nowMs) / 1000;

        long plannedDowntime = getPlannedDowntimeSeconds(deviceId);
        long shiftDuration = Math.max(0, shiftEndSec - shiftStartSec);
        long plannedRuntime = Math.max(0, shiftDuration - plannedDowntime);

        Map<String, Long> stateDurations = sumStateDurations(deviceId, shiftStartSec, shiftEndSec, nowMs / 1000);
        long workingDuration = stateDurations.getOrDefault(DeviceStateEnum.WORKING.name(), 0L);
        long faultDuration = stateDurations.getOrDefault(DeviceStateEnum.FAULT.name(), 0L);
        long unplannedDowntime = stateDurations.getOrDefault(DeviceStateEnum.STANDBY.name(), 0L)
                + faultDuration
                + stateDurations.getOrDefault(DeviceStateEnum.SHUTDOWN.name(), 0L);
        long actualRuntime = Math.max(0, plannedRuntime - unplannedDowntime);

        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(deviceId, shiftStartSec, shiftEndSec);
        long theoreticalCycle = getTheoreticalCycleSeconds(deviceId);

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

        writeMetricToRedis(factoryId, deviceId, uptimeRate, performanceRate, availabilityRate, faultRate, oee, nowMs / 1000);
    }

    private long getPlannedDowntimeSeconds(String deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(BigDecimal::longValue)
                .orElse(0L);
    }

    private Map<String, Long> sumStateDurations(String deviceId, long startSec, long endSec, long nowSec) {
        List<DeviceStateRecordDO> timelines = deviceStateRecordRepository.selectByRange(deviceId, startSec, endSec);
        Map<String, Long> result = new HashMap<>();
        for (DeviceStateRecordDO t : timelines) {
            Integer stateCode = t.getStateCode();
            if (stateCode == null) {
                continue;
            }
            // 将数字编码转换为状态名称
            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(stateCode);
            String state = stateEnum.name();
            long segStart = Math.max(startSec, t.getStartTs());
            long segEnd = Math.min(endSec, t.getEndTs() != null ? t.getEndTs() : nowSec);
            if (segEnd > segStart) {
                long dur = segEnd - segStart;
                result.merge(state.toUpperCase(), dur, Long::sum);
            }
        }
        return result;
    }

    private long getTheoreticalCycleSeconds(String deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(BigDecimal::longValue)
                .orElse(0L);
    }

    private void writeMetricToRedis(String factoryId, String deviceId,
                                    BigDecimal uptimeRate, BigDecimal performanceRate,
                                    BigDecimal availabilityRate, BigDecimal faultRate,
                                    BigDecimal oee, long updatedAtSec) {
        String key = String.format(RedisConstant.RT_METRIC, "none", defaultBlank(factoryId), defaultBlank(deviceId));
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


