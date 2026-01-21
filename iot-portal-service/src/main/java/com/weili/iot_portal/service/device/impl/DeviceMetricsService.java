package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceParamConfigRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.*;
import com.weili.iot_portal.domain.metrics.MetricCalculationContext;
import com.weili.iot_portal.domain.metrics.MetricCalculationResult;
import com.weili.iot_portal.domain.metrics.MetricCalculator;
import com.weili.iot_portal.service.cache.DeviceMetricsCacheService;
import com.weili.iot_portal.service.device.ICheckpointService;
import com.weili.iot_portal.service.device.IDeviceMetricsService;
import com.weili.iot_portal.service.device.util.StateDurationUtils;
import com.weili.iot_portal.service.device.util.StateDurationUtils.StateDurations;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    
    // 时间转换常量
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);

    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository deviceStateRecordRepository;
    private final DeviceParamConfigRepository deviceParamConfigRepository;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final IShiftConfigService shiftConfigService;
    private final DeviceMetricsCacheService deviceMetricsCacheService;
    private final ICheckpointService<CheckpointData> checkpointService;

    @Autowired
    public DeviceMetricsService(DeviceInfoRepository deviceInfoRepository,
                                DeviceStateRecordRepository deviceStateRecordRepository,
                                DeviceParamConfigRepository deviceParamConfigRepository,
                                DeviceProductionRecordRepository deviceProductionRecordRepository,
                                IShiftCalculationService shiftCalculationService,
                                IShiftConfigService shiftConfigService,
                                DeviceMetricsCacheService deviceMetricsCacheService,
                                @Qualifier("deviceMetricsCheckpointService")
                                ICheckpointService<CheckpointData> checkpointService) {
        this.deviceInfoRepository = deviceInfoRepository;
        this.deviceStateRecordRepository = deviceStateRecordRepository;
        this.deviceParamConfigRepository = deviceParamConfigRepository;
        this.deviceProductionRecordRepository = deviceProductionRecordRepository;
        this.shiftCalculationService = shiftCalculationService;
        this.shiftConfigService = shiftConfigService;
        this.deviceMetricsCacheService = deviceMetricsCacheService;
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

        // 过滤设备：只处理监控中、在用状态、且关联工厂的设备
        // 与班次指标汇总的逻辑保持一致
        Map<Long, List<DeviceInfoDO>> devicesByFactory = groupDevicesByFactory(allDevices);

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        int skippedCount = allDevices.size() - (int) filtered;
        if (skippedCount > 0) {
            skip += skippedCount;
            log.debug("实时指标计算: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)",
                    allDevices.size(), filtered, skippedCount);
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

        // 性能优化：批量查询所有设备的参数配置，避免每个设备都单独查询
        Map<Long, List<DeviceParamConfigDO>> deviceParamsMap = batchLoadDeviceParams(remainingDevices);

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
                    // 使用预加载的参数配置数据
                    List<DeviceParamConfigDO> deviceParams = deviceParamsMap.getOrDefault(device.getId(), Collections.emptyList());
                    calculateDeviceMetricsWithParams(device, deviceParams);
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

        // 过滤设备：只处理监控中、在用状态、且关联工厂的设备
        List<DeviceInfoDO> validDevices = filterValidDevices(allDevices);

        if (validDevices.isEmpty()) {
            log.debug("实时指标计算: 没有符合条件的设备需要处理");
            return MetricsCalculationResult.of(0, 0);
        }

        int skippedCount = allDevices.size() - validDevices.size();
        if (skippedCount > 0) {
            log.debug("实时指标计算: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)",
                    allDevices.size(), validDevices.size(), skippedCount);
        }

        int success = 0;
        int error = 0;

        for (DeviceInfoDO device : validDevices) {
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
    
    // ==================== 工具方法 ====================
    
    /**
     * 检查设备是否有效，可用于处理
     * <p>
     * 统一设备过滤条件（与班次指标汇总保持一致）：
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
    
    /**
     * 过滤有效设备
     * 
     * @param devices 设备列表
     * @return 有效设备列表
     */
    private List<DeviceInfoDO> filterValidDevices(List<DeviceInfoDO> devices) {
        return devices.stream()
                .filter(this::isDeviceValidForProcessing)
                .collect(Collectors.toList());
    }
    
    /**
     * 按工厂分组设备
     * 
     * @param devices 设备列表
     * @return 按工厂ID分组的设备Map
     */
    private Map<Long, List<DeviceInfoDO>> groupDevicesByFactory(List<DeviceInfoDO> devices) {
        return filterValidDevices(devices).stream()
                .collect(Collectors.groupingBy(DeviceInfoDO::getOrgFactoryId));
    }

    @Override
    public void calculateDeviceMetrics(DeviceInfoDO device) {
        // 查询设备参数配置（兼容单设备调用场景）
        List<DeviceParamConfigDO> deviceParams = deviceParamConfigRepository.selectCurrent(device.getId());
        calculateDeviceMetricsWithParams(device, deviceParams);
    }
    
    /**
     * 使用预加载的参数配置计算设备指标（批量处理优化版本）
     * 
     * @param device 设备信息
     * @param deviceParams 设备参数配置列表（已预加载）
     */
    private void calculateDeviceMetricsWithParams(DeviceInfoDO device, List<DeviceParamConfigDO> deviceParams) {
        // 1. 准备数据（使用预加载的参数配置）
        RealtimeCalculationData data = prepareCalculationDataWithParams(device, deviceParams);
        if (data == null) {
            return; // 数据准备失败，已记录日志
        }
        
        // 2. 验证数据并告警
        validateAndWarn(data);
        
        // 3. 构建计算上下文
        MetricCalculationContext context = buildCalculationContext(data);
        
        // 4. 计算指标
        MetricCalculationResult result = MetricCalculator.calculate(context);
        
        // 5. 验证性能率为0的原因并告警
        warnPerformanceRateZero(result, data);
        
        // 6. 转换并写入缓存（通过缓存服务）
        RealtimeMetricsPercentages percentages = convertToPercentages(result);
        RealtimeMetricSnapshot snapshot =
                new RealtimeMetricSnapshot(
                        percentages.getUptimeRate(),
                        percentages.getPerformanceRate(),
                        percentages.getAvailabilityRate(),
                        percentages.getFaultRate(),
                        percentages.getOee(),
                        data.getNowMs() / MILLIS_PER_SECOND
                );
        deviceMetricsCacheService.saveRealtimeMetrics(data.getFactoryId(), data.getDeviceId(), snapshot);
    }

    // ==================== 数据准备层 ====================
    
    /**
     * 准备实时指标计算所需的所有数据（使用预加载的参数配置）
     * 
     * @param device 设备信息
     * @param deviceParams 设备参数配置列表（已预加载）
     * @return 实时计算数据，如果数据准备失败则返回null
     */
    private RealtimeCalculationData prepareCalculationDataWithParams(DeviceInfoDO device, List<DeviceParamConfigDO> deviceParams) {
        Long factoryId = device.getOrgFactoryId();
        Long deviceId = device.getId();
        long nowMs = System.currentTimeMillis();
        
        // 1. 获取当前时间对应的班次日期（考虑跨天班次）
        LocalDate shiftDate = shiftCalculationService.getShiftDate(factoryId, deviceId, nowMs);
        if (shiftDate == null) {
            log.debug("无法计算班次日期，跳过设备: deviceId={}", deviceId);
            return null;
        }
        
        // 2. 直接使用班次日期查询状态记录（device_state_record表已记录计算好的班次日期）
        List<DeviceStateRecordDO> stateRecords = deviceStateRecordRepository.selectByShiftDateRange(
                deviceId, shiftDate, shiftDate);
        
        // 3. 获取班次配置，确定班次数量（2班制或3班制）
        com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO shiftConfig = 
                shiftConfigService.getCurrentConfiguration(factoryId, deviceId, nowMs);
        int shiftCount = (shiftConfig != null && shiftConfig.getShiftMode() != null) 
                ? shiftConfig.getShiftMode() 
                : 2; // 默认2班制
        
        // 4. 从预加载的参数配置中获取单个班次的计划停机时长（秒）
        long plannedDowntimePerShift = extractParameterValueFromList(deviceParams, PARAM_PLANNED_DOWNTIME, deviceId);
        // 计算一天的总计划停机时长：单个班次计划停机时长 × 班次数量
        // 例如：2班制，单个班次计划停机1小时，则一天总计划停机2小时
        long plannedDowntime = plannedDowntimePerShift * shiftCount;
        
        // 5. 使用工具类汇总状态持续时间（包括正在进行中的状态）
        // 注意：对于正在进行中的状态，使用当前时间作为结束时间
        StateDurations stateDurations = StateDurationUtils.sumStateDurationsFromRecords(stateRecords, nowMs);
        
        // 6. 计算已过日历时长（从该班次日期最早的状态记录开始时间到当前时间）
        // 如果状态记录为空，使用当前时间（避免除零错误）
        long dayStartMillis = stateRecords.isEmpty() 
                ? nowMs 
                : stateRecords.stream()
                        .mapToLong(r -> r.getStartTs() != null ? r.getStartTs() : Long.MAX_VALUE)
                        .min()
                        .orElse(nowMs);
        long dayEndMillis = nowMs; // 当前时间作为结束时间
        
        // 7. 计算当天时长和计划运行时长
        // 注意：实时指标计算中，计划运行时长使用当天时长减去一天的总计划停机时长
        long dayDurationMillis = Math.max(0, dayEndMillis - dayStartMillis);
        long plannedRuntimeMillis = Math.max(0, dayDurationMillis - plannedDowntime * MILLIS_PER_SECOND);
        long actualRuntimeMillis = Math.max(0, plannedRuntimeMillis - stateDurations.getUnplannedDowntimeMillis());
        
        // 8. 获取产量和理论节拍（如果未配置，会从 device_production_record 获取默认值）
        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(
                deviceId, dayStartMillis / MILLIS_PER_SECOND, dayEndMillis / MILLIS_PER_SECOND);
        long theoreticalCycle = extractParameterValueFromList(deviceParams, PARAM_THEORETICAL_CYCLE, deviceId);
        
        // 9. 计算已过日历时长（从该班次日期最早的状态记录开始时间到当前时间）
        long elapsedCalendarMillis = Math.max(1, nowMs - dayStartMillis);
        
        return new RealtimeCalculationData(
                factoryId, deviceId, nowMs, dayStartMillis, dayEndMillis,
                dayDurationMillis, plannedDowntime, plannedRuntimeMillis,
                stateDurations, actualRuntimeMillis, actualOutput, theoreticalCycle,
                elapsedCalendarMillis
        );
    }
    
    /**
     * 准备实时指标计算所需的所有数据（兼容单设备调用场景）
     * 
     * @param device 设备信息
     * @return 实时计算数据，如果数据准备失败则返回null
     * @deprecated 使用 {@link #prepareCalculationDataWithParams(DeviceInfoDO, List)} 代替
     */
    @Deprecated
    private RealtimeCalculationData prepareCalculationData(DeviceInfoDO device) {
        List<DeviceParamConfigDO> deviceParams = deviceParamConfigRepository.selectCurrent(device.getId());
        return prepareCalculationDataWithParams(device, deviceParams);
    }
    

    /**
     * 获取理论周期（秒）
     * 
     */
    @Deprecated
    private long getTheoreticalCycleSeconds(Long deviceId) {
        return extractParameterValue(deviceId, PARAM_THEORETICAL_CYCLE);
    }
    
    /**
     * 从设备参数配置列表中提取指定参数的值（批量处理优化版本）
     * <p>
     * 对于理论节拍（THEORETICAL_CYCLE），如果参数未配置或值为0，会尝试从 device_production_record 获取最新已完成记录的 duration_s 作为默认值
     * 
     * @param deviceParams 设备参数配置列表（已预加载）
     * @param parameterType 参数类型
     * @param deviceId 设备ID（用于获取默认值，仅当 parameterType 为 THEORETICAL_CYCLE 时使用）
     * @return 参数值（秒），如果不存在则返回0
     */
    private long extractParameterValueFromList(List<DeviceParamConfigDO> deviceParams, String parameterType, Long deviceId) {
        if (deviceParams == null || deviceParams.isEmpty()) {
            // 如果是理论节拍且未配置，尝试获取默认值
            if (PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(parameterType) && deviceId != null) {
                return getTheoreticalCycleDefaultValue(deviceId);
            }
            return 0L;
        }
        
        long value = deviceParams.stream()
                .filter(p -> parameterType.equalsIgnoreCase(p.getParameterType()))
                .findFirst()
                .map(DeviceParamConfigDO::getParameterValue)
                .map(BigDecimal::longValue)
                .orElse(0L);
        
        // 如果是理论节拍且值为0，尝试获取默认值
        if (PARAM_THEORETICAL_CYCLE.equalsIgnoreCase(parameterType) && value <= 0 && deviceId != null) {
            return getTheoreticalCycleDefaultValue(deviceId);
        }
        
        return value;
    }
    
    /**
     * 获取理论节拍默认值（从 device_production_record 获取最新已完成记录的 duration_s）
     * <p>
     * 注意：duration_s 字段实际存储的是毫秒，需要转换为秒
     * 
     * @param deviceId 设备ID
     * @return 理论节拍默认值（秒），如果不存在则返回0
     */
    private long getTheoreticalCycleDefaultValue(Long deviceId) {
        return deviceProductionRecordRepository.findLatestCompletedDurationS(deviceId)
                .map(durationMs -> durationMs / 1000L)  // 将毫秒转换为秒
                .orElse(0L);
    }
    
    /**
     * 从设备参数配置中提取指定参数的值（兼容单设备调用场景）
     * 
     * @param deviceId 设备ID
     * @param parameterType 参数类型
     * @return 参数值（秒），如果不存在则返回0
     * @deprecated 使用 {@link #extractParameterValueFromList(List, String, Long)} 代替
     */
    @Deprecated
    private long extractParameterValue(Long deviceId, String parameterType) {
        List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(deviceId);
        return extractParameterValueFromList(params, parameterType, deviceId);
    }
    
    /**
     * 批量加载设备参数配置
     * <p>
     * 性能优化：一次性查询所有设备的参数配置，避免每个设备都单独查询一次数据库
     * 
     * @param devices 设备列表
     * @return 设备ID到参数配置列表的Map
     */
    private Map<Long, List<DeviceParamConfigDO>> batchLoadDeviceParams(List<DeviceInfoDO> devices) {
        Map<Long, List<DeviceParamConfigDO>> result = new HashMap<>();
        if (devices == null || devices.isEmpty()) {
            return result;
        }
        
        // 批量查询：虽然 Repository 没有批量查询方法，但我们可以循环查询并缓存结果
        // 这样可以避免在 prepareCalculationData 中重复查询
        // 注意：如果 Repository 后续添加了批量查询方法，可以进一步优化
        for (DeviceInfoDO device : devices) {
            List<DeviceParamConfigDO> params = deviceParamConfigRepository.selectCurrent(device.getId());
            if (params != null && !params.isEmpty()) {
                result.put(device.getId(), params);
            }
        }
        
        return result;
    }
    
    // ==================== 数据验证层 ====================
    
    /**
     * 验证数据并记录告警
     * 
     * @param data 实时计算数据
     */
    private void validateAndWarn(RealtimeCalculationData data) {
        // 验证理论节拍
        if (data.getTheoreticalCycle() <= 0) {
            log.warn("实时指标计算: 理论节拍参数缺失或无效（<=0），将导致性能率和OEE为0: deviceId={}, factoryId={}, " +
                    "shiftStartTs={}, shiftEndTs={}, theoreticalCycle={}",
                    data.getDeviceId(), data.getFactoryId(), 
                    data.getShiftStartMillis(), data.getShiftEndMillis(), data.getTheoreticalCycle());
        }
        
        // 验证产量数据
        long actualRuntimeSec = data.getActualRuntimeMillis() / MILLIS_PER_SECOND;
        if (data.getActualOutput() == 0 && actualRuntimeSec > 0) {
            log.warn("实时指标计算: 产量数据缺失（产量为0但设备有运行时间），将导致性能率和OEE为0: deviceId={}, factoryId={}, " +
                    "shiftStartTs={}, shiftEndTs={}, actualRuntimeSec={}, actualOutput={}",
                    data.getDeviceId(), data.getFactoryId(), 
                    data.getShiftStartMillis(), data.getShiftEndMillis(), actualRuntimeSec, data.getActualOutput());
        }
    }
    
    /**
     * 验证性能率为0的原因并记录告警
     * 
     * @param result 指标计算结果
     * @param data 实时计算数据
     */
    private void warnPerformanceRateZero(MetricCalculationResult result, RealtimeCalculationData data) {
        long actualRuntimeSec = data.getActualRuntimeMillis() / MILLIS_PER_SECOND;
        if (result.getPerformance().compareTo(BigDecimal.ZERO) == 0 && actualRuntimeSec > 0) {
            if (data.getTheoreticalCycle() <= 0) {
                log.warn("实时指标计算: 性能率为0（理论节拍缺失）: deviceId={}, factoryId={}, " +
                        "shiftStartTs={}, shiftEndTs={}, actualRuntimeSec={}, theoreticalCycle={}",
                        data.getDeviceId(), data.getFactoryId(), 
                        data.getShiftStartMillis(), data.getShiftEndMillis(), actualRuntimeSec, data.getTheoreticalCycle());
            } else if (data.getActualOutput() <= 0) {
                log.warn("实时指标计算: 性能率为0（产量缺失）: deviceId={}, factoryId={}, " +
                        "shiftStartTs={}, shiftEndTs={}, actualRuntimeSec={}, actualOutput={}, theoreticalCycle={}",
                        data.getDeviceId(), data.getFactoryId(), 
                        data.getShiftStartMillis(), data.getShiftEndMillis(), actualRuntimeSec, 
                        data.getActualOutput(), data.getTheoreticalCycle());
            }
        }
    }
    
    // ==================== 计算层 ====================
    
    /**
     * 构建指标计算上下文
     * 
     * @param data 实时计算数据
     * @return 指标计算上下文
     */
    private MetricCalculationContext buildCalculationContext(RealtimeCalculationData data) {
        long plannedDowntimeMillis = data.getPlannedDowntime() * MILLIS_PER_SECOND;
        // 实时计算中，质量率固定为100%，所以合格数量等于实际产量
        long qualifiedOutput = data.getActualOutput();
        
        return new MetricCalculationContext(
                data.getShiftDurationMillis(),           // 当天时长（毫秒，从班次日期第一个班次开始到当前时间）
                data.getPlannedDowntime(),               // 计划停机时长（秒）
                plannedDowntimeMillis,                    // 计划停机时长（毫秒）
                data.getPlannedRuntimeMillis(),           // 计划运行时长（毫秒）
                data.getStateDurations().getStandbyMillis(),      // 待机时长（毫秒）
                data.getStateDurations().getFaultMillis(),        // 故障时长（毫秒）
                data.getStateDurations().getShutdownMillis(),     // 关机时长（毫秒）
                data.getStateDurations().getWorkingMillis(),      // 加工时长（毫秒）
                data.getStateDurations().getUnplannedDowntimeMillis(), // 非计划停机时长（毫秒）
                data.getActualRuntimeMillis(),           // 实际运行时长（毫秒）
                data.getActualOutput(),                  // 实际产量（件）
                qualifiedOutput,                         // 合格数量（件），实时计算中等于实际产量
                data.getTheoreticalCycle(),              // 理论节拍（秒）
                data.getElapsedCalendarMillis()          // 可用率计算的分母（已过日历时长，毫秒）
        );
    }
    
    /**
     * 将指标计算结果转换为小数形式（用于Redis存储，统一存储为0-1范围的小数）
     * 
     * @param result 指标计算结果
     * @return 小数形式的指标（0-1范围）
     */
    private RealtimeMetricsPercentages convertToPercentages(MetricCalculationResult result) {
        // 从 metrics Map 中获取 faultRate（百分比形式），需要除以100转换为小数（0-1）
        BigDecimal faultRatePercent = extractFaultRateFromMetrics(result.getMetrics());
        BigDecimal faultRate = faultRatePercent.divide(PERCENTAGE_MULTIPLIER, 4, RoundingMode.HALF_UP);
        
        // 所有指标统一存储为小数形式（0-1范围），与数据库字段格式保持一致
        return new RealtimeMetricsPercentages(
                result.getAvailability(),      // Availability: 小数（0-1）
                result.getPerformance(),        // Performance: 小数（0-1）
                faultRate,                     // Fault Rate: 小数（0-1）
                result.getOee(),               // OEE: 小数（0-1）
                result.getUtilizationRate()     // Utilization Rate: 小数（0-1）
        );
    }
    
    /**
     * 从 metrics Map 中提取故障率
     * 
     * @param metrics 指标Map
     * @return 故障率（百分比形式 0-100），如果不存在则返回0
     */
    private BigDecimal extractFaultRateFromMetrics(Map<String, Object> metrics) {
        if (metrics == null) {
            return BigDecimal.ZERO;
        }
        Object faultRateObj = metrics.get("faultRate");
        if (faultRateObj == null) {
            return BigDecimal.ZERO;
        }
        if (faultRateObj instanceof BigDecimal) {
            return (BigDecimal) faultRateObj;
        }
        if (faultRateObj instanceof Number) {
            return BigDecimal.valueOf(((Number) faultRateObj).doubleValue());
        }
        try {
            return new BigDecimal(faultRateObj.toString());
        } catch (Exception e) {
            log.warn("无法解析故障率: {}", faultRateObj, e);
            return BigDecimal.ZERO;
        }
    }
    @Override
    public Optional<RealtimeMetricSnapshot> getDeviceRealtimeMetrics(Long factoryId, Long deviceId) {
        return deviceMetricsCacheService.getDeviceRealtimeMetrics(factoryId, deviceId);
    }

    @Override
    public Map<Long, RealtimeMetricSnapshot> batchGetDeviceRealtimeMetrics(Long factoryId, List<Long> deviceIds) {
        return deviceMetricsCacheService.batchGetDeviceRealtimeMetrics(factoryId, deviceIds);
    }
    
    // ==================== 内部类 ====================
    
    /**
     * 实时计算数据封装类
     * <p>
     * 注意：实时指标计算使用"班次日期"的时间范围（从该班次日期的第一个班次开始时间到当前时间），
     * 而不是"当前班次"的时间范围。
     * </p>
     */
    private static class RealtimeCalculationData {
        private final Long factoryId;
        private final Long deviceId;
        private final long nowMs;
        private final long shiftStartMillis;  // 班次日期第一个班次的开始时间（毫秒）
        private final long shiftEndMillis;    // 当前时间（毫秒）
        private final long shiftDurationMillis;  // 当天时长（毫秒，从班次日期第一个班次开始到当前时间）
        private final long plannedDowntime;  // 一天的总计划停机时长（秒）= 单个班次计划停机时长 × 班次数量（2班制×2，3班制×3）
        private final long plannedRuntimeMillis;
        private final StateDurations stateDurations;
        private final long actualRuntimeMillis;
        private final long actualOutput;
        private final long theoreticalCycle;
        private final long elapsedCalendarMillis;  // 已过日历时长（毫秒，从班次日期第一个班次开始到当前时间）
        
        public RealtimeCalculationData(Long factoryId, Long deviceId, long nowMs,
                                      long shiftStartMillis, long shiftEndMillis,
                                      long shiftDurationMillis, long plannedDowntime, long plannedRuntimeMillis,
                                      StateDurations stateDurations, long actualRuntimeMillis, 
                                      long actualOutput, long theoreticalCycle, long elapsedCalendarMillis) {
            this.factoryId = factoryId;
            this.deviceId = deviceId;
            this.nowMs = nowMs;
            this.shiftStartMillis = shiftStartMillis;
            this.shiftEndMillis = shiftEndMillis;
            this.shiftDurationMillis = shiftDurationMillis;
            this.plannedDowntime = plannedDowntime;
            this.plannedRuntimeMillis = plannedRuntimeMillis;
            this.stateDurations = stateDurations;
            this.actualRuntimeMillis = actualRuntimeMillis;
            this.actualOutput = actualOutput;
            this.theoreticalCycle = theoreticalCycle;
            this.elapsedCalendarMillis = elapsedCalendarMillis;
        }
        
        public Long getFactoryId() { return factoryId; }
        public Long getDeviceId() { return deviceId; }
        public long getNowMs() { return nowMs; }
        public long getShiftStartMillis() { return shiftStartMillis; }
        public long getShiftEndMillis() { return shiftEndMillis; }
        public long getShiftDurationMillis() { return shiftDurationMillis; }
        public long getPlannedDowntime() { return plannedDowntime; }
        public long getPlannedRuntimeMillis() { return plannedRuntimeMillis; }
        public StateDurations getStateDurations() { return stateDurations; }
        public long getActualRuntimeMillis() { return actualRuntimeMillis; }
        public long getActualOutput() { return actualOutput; }
        public long getTheoreticalCycle() { return theoreticalCycle; }
        public long getElapsedCalendarMillis() { return elapsedCalendarMillis; }
    }
    
    
    /**
     * 实时指标百分比封装类
     */
    private static class RealtimeMetricsPercentages {
        private final BigDecimal uptimeRate;
        private final BigDecimal performanceRate;
        private final BigDecimal faultRate;
        private final BigDecimal oee;
        private final BigDecimal availabilityRate;
        
        public RealtimeMetricsPercentages(BigDecimal uptimeRate, BigDecimal performanceRate,
                                        BigDecimal faultRate, BigDecimal oee, BigDecimal availabilityRate) {
            this.uptimeRate = uptimeRate;
            this.performanceRate = performanceRate;
            this.faultRate = faultRate;
            this.oee = oee;
            this.availabilityRate = availabilityRate;
        }
        
        public BigDecimal getUptimeRate() { return uptimeRate; }
        public BigDecimal getPerformanceRate() { return performanceRate; }
        public BigDecimal getFaultRate() { return faultRate; }
        public BigDecimal getOee() { return oee; }
        public BigDecimal getAvailabilityRate() { return availabilityRate; }
    }
}

