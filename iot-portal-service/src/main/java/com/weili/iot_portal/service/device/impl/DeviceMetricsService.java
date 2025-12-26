package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceParamConfigRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.*;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsService;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备实时指标计算服务实现
 */
@Slf4j
@Service
public class DeviceMetricsService implements IDeviceMetricsService {

    private static final String PARAM_PLANNED_DOWNTIME = "PLANNED_DOWNTIME";
    private static final String PARAM_THEORETICAL_CYCLE = "THEORETICAL_CYCLE";

    @Value("${rt.metrics.ttl-seconds:600}")
    private long ttlSeconds;

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository deviceStateRecordRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ICheckpointService<CheckpointData> checkpointService;

    @Autowired
    public DeviceMetricsService(DeviceInfoRepository deviceInfoRepository,
                                DeviceStateRecordRepository deviceStateRecordRepository,
                                DeviceParamConfigRepository deviceParamConfigRepository,
                                DeviceProductionRecordRepository deviceProductionRecordRepository,
                                IShiftCalculationService shiftCalculationService,
                                StringRedisTemplate stringRedisTemplate,
                                @Qualifier("deviceMetricsCheckpointService")
                                ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.deviceStateRecordRepository = deviceStateRecordRepository;
        this.deviceParamConfigRepository = deviceParamConfigRepository;
        this.deviceProductionRecordRepository = deviceProductionRecordRepository;
        this.shiftCalculationService = shiftCalculationService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.checkpointService = checkpointService;
    }

    @Override
    public BatchProcessResult processAllDevicesWithCheckpoint(
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis) {

        List<DeviceInfoDO> allDevices = deviceInfoRepository.findActiveWithFactory();
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
            log.warn("设备指标计算: 有 {} 个设备未关联工厂，已跳过", allDevices.size() - filtered);
        }

        for (Map.Entry<Long, List<DeviceInfoDO>> factoryEntry : devicesByFactory.entrySet()) {
            Long factoryId = factoryEntry.getKey();
            List<DeviceInfoDO> devices = factoryEntry.getValue();
            try {
                BatchProcessResult factoryResult = processFactoryDevicesWithCheckpoint(
                        factoryId, devices, calculationTimeSeconds, batchSize, timeoutMillis);
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
    public BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis) {

        // 加载检查点，获取已处理的设备ID
        Set<Long> processedDeviceIds = checkpointService.getProcessedDeviceIds(
                factoryId, calculationTimeSeconds);

        // 过滤已处理的设备
        List<DeviceInfoDO> remainingDevices = devices.stream()
                .filter(device -> !processedDeviceIds.contains(device.getId()))
                .collect(Collectors.toList());

        if (remainingDevices.isEmpty()) {
            // 所有设备已处理，清除检查点
            checkpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
            return BatchProcessResult.completed(0, 0, 0);
        }

        if (!processedDeviceIds.isEmpty()) {
            log.info("从检查点恢复: 工厂={}, 已处理={}, 剩余={}",
                    factoryId, processedDeviceIds.size(), remainingDevices.size());
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
                    calculateDeviceMetrics(device);
                    successCount++;
                    newProcessedIds.add(device.getId());
                    processedDeviceIds.add(device.getId());
                } catch (Exception e) {
                    errorCount++;
                    log.error("计算指标失败: factoryId={}, deviceId={}, deviceCode={}",
                            factoryId, device.getId(), device.getDeviceCode(), e);
                    // 继续处理下一个设备，不中断
                }
            }

            // 每批处理完后更新检查点
            if (!newProcessedIds.isEmpty()) {
                List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
                checkpointService.saveCheckpoint(factoryId, calculationTimeSeconds, allProcessedIds);
                newProcessedIds.clear();
            }

            // 检查是否超时
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMillis) {
                log.warn("处理超时，保存检查点: 工厂={}, 已处理={}, 剩余={}",
                        factoryId, processedDeviceIds.size(), remainingDevices.size() - processedDeviceIds.size());
                return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
            }
        }

        // 所有设备都处理完成，清除检查点
        if (processedDeviceIds.size() >= devices.size()) {
            checkpointService.clearCheckpoint(factoryId, calculationTimeSeconds);
            return BatchProcessResult.completed(successCount, skipCount, errorCount);
        } else {
            // 还有未处理的设备，保存检查点
            List<Long> allProcessedIds = new ArrayList<>(processedDeviceIds);
            checkpointService.saveCheckpoint(factoryId, calculationTimeSeconds, allProcessedIds);
            return BatchProcessResult.incomplete(successCount, skipCount, errorCount);
        }
    }

    @Override
    public MetricsCalculationResult calculateAllDevicesMetrics() {
        List<DeviceInfoDO> allDevices = deviceInfoRepository.findActiveWithFactory();
        if (allDevices.isEmpty()) {
            return MetricsCalculationResult.of(0, 0);
        }

        int success = 0;
        int error = 0;

        for (DeviceInfoDO device : allDevices) {
            try {
                calculateDeviceMetrics(device);
                success++;
            } catch (Exception e) {
                error++;
                log.error("计算指标失败 deviceId={}", device.getId(), e);
            }
        }

        return MetricsCalculationResult.of(success, error);
    }

    @Override
    public void calculateDeviceMetrics(DeviceInfoDO device) {
        Long factoryId = device.getOrgFactoryId();
        Long deviceId = device.getId();

        long nowMs = System.currentTimeMillis();
        ShiftTimeRange shift = shiftCalculationService.calculateShiftRange(factoryId, deviceId, nowMs);
        if (shift == null || shift.getStartTs() == null) {
            log.debug("无法计算班次时间范围，跳过设备: deviceId={}", deviceId);
            return;
        }

        long shiftStartMillis = shift.getStartTs();
        long shiftEndMillis = shift.getEndTs() != null ? shift.getEndTs() : nowMs;

        // 获取计划停机时间（秒）
        long plannedDowntime = getPlannedDowntimeSeconds(deviceId);
        // 转换为毫秒进行计算
        long shiftDurationMillis = Math.max(0, shiftEndMillis - shiftStartMillis);
        long plannedRuntimeMillis = Math.max(0, shiftDurationMillis - plannedDowntime * 1000);

        // 汇总状态持续时间（使用毫秒）
        Map<String, Long> stateDurations = sumStateDurations(deviceId, shiftStartMillis, shiftEndMillis, nowMs);
        long workingDurationMillis = stateDurations.getOrDefault(DeviceStateEnum.WORKING.name(), 0L);
        long faultDurationMillis = stateDurations.getOrDefault(DeviceStateEnum.FAULT.name(), 0L);
        long unplannedDowntimeMillis = stateDurations.getOrDefault(DeviceStateEnum.STANDBY.name(), 0L)
                + faultDurationMillis
                + stateDurations.getOrDefault(DeviceStateEnum.SHUTDOWN.name(), 0L);
        long actualRuntimeMillis = Math.max(0, plannedRuntimeMillis - unplannedDowntimeMillis);

        // 获取实际产量和理论周期
        // countCompletedInRange 方法需要秒单位，所以需要转换
        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(deviceId, shiftStartMillis / 1000, shiftEndMillis / 1000);
        long theoreticalCycle = getTheoreticalCycleSeconds(deviceId);

        // 计算时间开动率（Uptime Rate）- 所有时长都是毫秒，计算时转换为秒
        long plannedRuntimeSec = plannedRuntimeMillis / 1000;
        long actualRuntimeSec = actualRuntimeMillis / 1000;
        BigDecimal uptimeRate = plannedRuntimeSec == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(actualRuntimeSec)
                .divide(BigDecimal.valueOf(plannedRuntimeSec), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 计算性能率（Performance Rate）
        BigDecimal performanceRate = (actualRuntimeSec == 0 || theoreticalCycle <= 0 || actualOutput <= 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(actualOutput)
                .multiply(BigDecimal.valueOf(theoreticalCycle))
                .divide(BigDecimal.valueOf(actualRuntimeSec), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 计算可用率（Availability Rate）
        long elapsedCalendarSec = Math.max(1, (nowMs / 1000) - (shiftStartMillis / 1000));
        long workingDurationSec = workingDurationMillis / 1000;
        BigDecimal availabilityRate = BigDecimal.valueOf(workingDurationSec)
                .divide(BigDecimal.valueOf(elapsedCalendarSec), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 计算故障率（Fault Rate）
        long faultDurationSec = faultDurationMillis / 1000;
        BigDecimal faultRate = plannedRuntimeSec == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(faultDurationSec)
                .divide(BigDecimal.valueOf(plannedRuntimeSec), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // 计算OEE（Overall Equipment Effectiveness）
        // 实时 OEE：合格品率默认 100%
        BigDecimal oee = uptimeRate
                .multiply(performanceRate)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);

        // 写入Redis
        writeMetricToRedis(factoryId, deviceId, uptimeRate, performanceRate, availabilityRate, faultRate, oee, nowMs / 1000);
    }

    /**
     * 获取计划停机时间（秒）
     */
    private long getPlannedDowntimeSeconds(Long deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_PLANNED_DOWNTIME.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(BigDecimal::longValue)
                .orElse(0L);
    }

    /**
     * 汇总状态持续时间
     * 
     * @param deviceId 设备ID
     * @param startMillis 开始时间（毫秒）
     * @param endMillis 结束时间（毫秒）
     * @param nowMillis 当前时间（毫秒）
     * @return 状态持续时长Map，key为状态名称，value为持续时长（毫秒）
     */
    private Map<String, Long> sumStateDurations(Long deviceId, long startMillis, long endMillis, long nowMillis) {
        List<DeviceStateRecordDO> timelines = deviceStateRecordRepository.selectByRange(deviceId, startMillis, endMillis);
        Map<String, Long> result = new HashMap<>();
        for (DeviceStateRecordDO t : timelines) {
            Integer stateCode = t.getStateCode();
            if (stateCode == null) {
                continue;
            }
            // 将数字编码转换为状态名称
            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(stateCode);
            String state = stateEnum.name();
            long segStart = Math.max(startMillis, t.getStartTs());
            long segEnd = Math.min(endMillis, t.getEndTs() != null ? t.getEndTs() : nowMillis);
            if (segEnd > segStart) {
                long dur = segEnd - segStart;
                result.merge(state.toUpperCase(), dur, Long::sum);
            }
        }
        return result;
    }

    /**
     * 获取理论周期（秒）
     */
    private long getTheoreticalCycleSeconds(Long deviceId) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return params.stream()
                .filter(p -> PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(BigDecimal::longValue)
                .orElse(0L);
    }

    /**
     * 写入指标到Redis
     */
    private void writeMetricToRedis(Long factoryId, Long deviceId,
                                    BigDecimal uptimeRate, BigDecimal performanceRate,
                                    BigDecimal availabilityRate, BigDecimal faultRate,
                                    BigDecimal oee, long updatedAtSec) {
        String key = String.format(RedisConstant.RT_METRIC, defaultBlank(factoryId), defaultBlank(deviceId));
        Map<String, String> payload = new HashMap<>();
        payload.put("metric.uptimeRate", uptimeRate.toPlainString());
        payload.put("metric.performanceRate", performanceRate.toPlainString());
        payload.put("metric.availabilityRate", availabilityRate.toPlainString());
        payload.put("metric.faultRate", faultRate.toPlainString());
        payload.put("metric.oee", oee.toPlainString());
        payload.put("updatedAt", String.valueOf(updatedAtSec));
        stringRedisTemplate.opsForHash().putAll(key, payload);
        stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<RealtimeMetricSnapshot> getDeviceRealtimeMetrics(Long factoryId, Long deviceId) {
        String key = String.format(RedisConstant.RT_METRIC, defaultBlank(factoryId), defaultBlank(deviceId));
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal uptime = parseDecimal(map.get("metric.uptimeRate"));
            BigDecimal performance = parseDecimal(map.get("metric.performanceRate"));
            BigDecimal availability = parseDecimal(map.get("metric.availabilityRate"));
            BigDecimal fault = parseDecimal(map.get("metric.faultRate"));
            BigDecimal oee = parseDecimal(map.get("metric.oee"));
            long updatedAt = parseLong(map.get("updatedAt"), 0L);
            return Optional.of(new RealtimeMetricSnapshot(uptime, performance, availability, fault, oee, updatedAt));
        } catch (Exception e) {
            log.warn("读取实时指标解析失败: key={}", key, e);
            return Optional.empty();
        }
    }

    private BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return java.math.BigDecimal.ZERO;
        }
        return new java.math.BigDecimal(v.toString());
    }

    private long parseLong(Object v, long defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException ex) {
            return defaultVal;
        }
    }

    private String defaultBlank(Long v) {
        return v == null ? "none" : v.toString();
    }
}

