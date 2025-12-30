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
import com.weili.iot_portal.service.metrics.MetricCalculator;
import com.weili.iot_portal.service.metrics.MetricCalculationContext;
import com.weili.iot_portal.service.metrics.MetricCalculationResult;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private static final String DEVICE_STATUS_ACTIVE = "ACTIVE";
    
    // 时间转换常量
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final BigDecimal PERCENTAGE_MULTIPLIER = BigDecimal.valueOf(100);

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

        // 过滤设备：只处理监控中、在用状态、且关联工厂的设备
        // 与班次指标汇总的逻辑保持一致
        Map<Long, List<DeviceInfoDO>> devicesByFactory = groupDevicesByFactory(allDevices);

        long filtered = devicesByFactory.values().stream().mapToLong(List::size).sum();
        int skippedCount = allDevices.size() - (int) filtered;
        if (skippedCount > 0) {
            skip += skippedCount;
            log.info("实时指标计算: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)", 
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
            log.info("实时指标计算: 没有符合条件的设备需要处理");
            return MetricsCalculationResult.of(0, 0);
        }

        int skippedCount = allDevices.size() - validDevices.size();
        if (skippedCount > 0) {
            log.info("实时指标计算: 总设备数={}, 符合条件设备数={}, 已跳过={} (未监控/非在用/未关联工厂)", 
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
        
        // 6. 转换并写入Redis
        RealtimeMetricsPercentages percentages = convertToPercentages(result);
        writeMetricsToRedis(data.getFactoryId(), data.getDeviceId(), percentages, data.getNowMs());
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
        
        // 1. 计算班次时间范围
        ShiftTimeRange shift = shiftCalculationService.calculateShiftRange(factoryId, deviceId, nowMs);
        if (shift == null || shift.getStartTs() == null) {
            log.debug("无法计算班次时间范围，跳过设备: deviceId={}", deviceId);
            return null;
        }
        
        long shiftStartMillis = shift.getStartTs();
        // 实时指标计算：如果班次未结束，使用当前时间；如果班次已结束，使用班次结束时间
        long shiftEndMillis = shift.getEndTs() != null && shift.getEndTs() <= nowMs 
                ? shift.getEndTs() : nowMs;
        
        // 2. 从预加载的参数配置中获取计划停机时长
        long plannedDowntime = extractParameterValueFromList(deviceParams, PARAM_PLANNED_DOWNTIME, deviceId);
        
        // 3. 计算班次时长和计划运行时长
        long shiftDurationMillis = Math.max(0, shiftEndMillis - shiftStartMillis);
        long plannedRuntimeMillis = Math.max(0, shiftDurationMillis - plannedDowntime * MILLIS_PER_SECOND);
        
        // 4. 汇总状态持续时间
        Map<String, Long> stateDurationsMap = sumStateDurations(deviceId, shiftStartMillis, shiftEndMillis, nowMs);
        StateDurations stateDurations = extractStateDurations(stateDurationsMap);
        long actualRuntimeMillis = Math.max(0, plannedRuntimeMillis - stateDurations.getUnplannedDowntimeMillis());
        
        // 5. 获取产量和理论节拍（如果未配置，会从 device_production_record 获取默认值）
        long actualOutput = deviceProductionRecordRepository.countCompletedInRange(
                deviceId, shiftStartMillis / MILLIS_PER_SECOND, shiftEndMillis / MILLIS_PER_SECOND);
        long theoreticalCycle = extractParameterValueFromList(deviceParams, PARAM_THEORETICAL_CYCLE, deviceId);
        
        // 6. 计算已过日历时长
        long elapsedCalendarMillis = Math.max(1, nowMs - shiftStartMillis);
        
        return new RealtimeCalculationData(
                factoryId, deviceId, nowMs, shiftStartMillis, shiftEndMillis,
                shiftDurationMillis, plannedDowntime, plannedRuntimeMillis,
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
     * 从状态持续时间Map中提取各状态的时长
     * 
     * @param stateDurations 状态持续时间Map
     * @return 状态持续时间封装对象
     */
    private StateDurations extractStateDurations(Map<String, Long> stateDurations) {
        return new StateDurations(
                stateDurations.getOrDefault(DeviceStateEnum.WORKING.name(), 0L),
                stateDurations.getOrDefault(DeviceStateEnum.STANDBY.name(), 0L),
                stateDurations.getOrDefault(DeviceStateEnum.FAULT.name(), 0L),
                stateDurations.getOrDefault(DeviceStateEnum.SHUTDOWN.name(), 0L)
        );
    }

    /**
     * 汇总状态持续时间
     * <p>
     * 说明：
     * <ul>
     *   <li>统计已结束的状态记录（endTs != null）</li>
     *   <li>统计正在进行中的状态（endTs == null），使用当前时间作为结束时间</li>
     *   <li>计算状态记录在统计时间范围内的持续时间</li>
     *   <li>实时指标计算需要反映设备的当前状态，所以应该统计正在进行中的状态</li>
     * </ul>
     * 
     * @param deviceId 设备ID
     * @param startMillis 开始时间（毫秒）
     * @param endMillis 结束时间（毫秒）
     * @param nowMillis 当前时间（毫秒），用于正在进行中的状态
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
            
            // 计算状态记录在统计时间范围内的持续时间
            // segStart: 状态开始时间与统计开始时间的较大值
            // segEnd: 状态结束时间（如果正在进行中，使用当前时间）与统计结束时间的较小值
            long segStart = Math.max(startMillis, t.getStartTs());
            long segEnd;
            if (t.getEndTs() != null) {
                // 已结束的状态：使用状态结束时间
                segEnd = Math.min(endMillis, t.getEndTs());
            } else {
                // 正在进行中的状态：使用当前时间（但不能超过统计结束时间）
                segEnd = Math.min(endMillis, nowMillis);
            }
            
            if (segEnd > segStart) {
                long dur = segEnd - segStart;
                result.merge(state.toUpperCase(), dur, Long::sum);
            }
        }
        return result;
    }

    /**
     * 获取理论周期（秒）
     * 
     * @deprecated 使用 {@link #extractParameterValueFromList(List, String)} 代替
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
     * 
     * @param deviceId 设备ID
     * @return 理论节拍默认值（秒），如果不存在则返回0
     */
    private long getTheoreticalCycleDefaultValue(Long deviceId) {
        return deviceProductionRecordRepository.findLatestCompletedDurationS(deviceId)
                .map(Integer::longValue)
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
                data.getShiftDurationMillis(),           // 班次时长（毫秒）
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
     * 将指标计算结果转换为百分比形式（用于Redis存储）
     * 
     * @param result 指标计算结果
     * @return 百分比形式的指标
     */
    private RealtimeMetricsPercentages convertToPercentages(MetricCalculationResult result) {
        // 从 metrics Map 中获取 faultRate（已经是百分比形式）
        BigDecimal faultRate = extractFaultRateFromMetrics(result.getMetrics());
        
        return new RealtimeMetricsPercentages(
                result.getAvailability().multiply(PERCENTAGE_MULTIPLIER),      // Uptime Rate
                result.getPerformance().multiply(PERCENTAGE_MULTIPLIER),        // Performance Rate
                faultRate,                                                     // Fault Rate (已经是百分比)
                result.getOee().multiply(PERCENTAGE_MULTIPLIER),               // OEE
                result.getUtilizationRate().multiply(PERCENTAGE_MULTIPLIER)    // Availability Rate
        );
    }
    
    /**
     * 从 metrics Map 中提取故障率
     * 
     * @param metrics 指标Map
     * @return 故障率（百分比），如果不存在则返回0
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
    
    // ==================== 存储层 ====================
    
    /**
     * 写入指标到Redis
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param metrics 百分比形式的指标
     * @param nowMs 当前时间（毫秒）
     */
    private void writeMetricsToRedis(Long factoryId, Long deviceId, 
                                    RealtimeMetricsPercentages metrics, long nowMs) {
        String key = buildRedisKey(factoryId, deviceId);
        Map<String, String> payload = buildRedisPayload(metrics, nowMs / MILLIS_PER_SECOND);
        
        try {
            stringRedisTemplate.opsForHash().putAll(key, payload);
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.error("写入Redis失败: key={}", key, e);
        }
    }
    
    /**
     * 生成Redis key
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis key
     */
    private String buildRedisKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_METRIC, defaultBlank(factoryId), defaultBlank(deviceId));
    }
    
    /**
     * 构建Redis payload
     * 
     * @param metrics 百分比形式的指标
     * @param updatedAtSec 更新时间（秒）
     * @return Redis payload Map
     */
    private Map<String, String> buildRedisPayload(RealtimeMetricsPercentages metrics, long updatedAtSec) {
        Map<String, String> payload = new HashMap<>();
        payload.put("metric.uptimeRate", metrics.getUptimeRate().toPlainString());
        payload.put("metric.performanceRate", metrics.getPerformanceRate().toPlainString());
        payload.put("metric.availabilityRate", metrics.getAvailabilityRate().toPlainString());
        payload.put("metric.faultRate", metrics.getFaultRate().toPlainString());
        payload.put("metric.oee", metrics.getOee().toPlainString());
        payload.put("updatedAt", String.valueOf(updatedAtSec));
        return payload;
    }
    
    /**
     * 写入指标到Redis（保留原方法以保持向后兼容）
     * 
     * @deprecated 使用 {@link #writeMetricsToRedis(Long, Long, RealtimeMetricsPercentages, long)} 代替
     */
    @Deprecated
    private void writeMetricToRedis(Long factoryId, Long deviceId,
                                    BigDecimal uptimeRate, BigDecimal performanceRate,
                                    BigDecimal availabilityRate, BigDecimal faultRate,
                                    BigDecimal oee, long updatedAtSec) {
        String key = buildRedisKey(factoryId, deviceId);
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

    @Override
    public Map<Long, RealtimeMetricSnapshot> batchGetDeviceRealtimeMetrics(Long factoryId, List<Long> deviceIds) {
        Map<Long, RealtimeMetricSnapshot> result = new HashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }

        // 性能优化：使用 Redis Pipeline 批量读取
        List<String> keys = deviceIds.stream()
                .map(deviceId -> String.format(RedisConstant.RT_METRIC, defaultBlank(factoryId), defaultBlank(deviceId)))
                .collect(Collectors.toList());

        try {
            // 性能优化：使用 Redis Pipeline 批量执行，减少网络往返次数
            // 注意：executePipelined 会自动处理序列化，返回的是 StringRedisTemplate 序列化后的结果
            List<Object> pipelineResults = stringRedisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String key : keys) {
                            connection.hGetAll(key.getBytes());
                        }
                        return null;
                    }
            );

            // 解析批量读取结果
            // executePipelined 返回的 Map 键值都是 String 类型（StringRedisTemplate 自动序列化）
            for (int i = 0; i < deviceIds.size() && i < pipelineResults.size(); i++) {
                Long deviceId = deviceIds.get(i);
                Object resultObj = pipelineResults.get(i);
                
                if (resultObj == null) {
                    continue;
                }
                
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) resultObj;
                
                if (map != null && !map.isEmpty()) {
                    try {
                        BigDecimal uptime = parseDecimal(map.get("metric.uptimeRate"));
                        BigDecimal performance = parseDecimal(map.get("metric.performanceRate"));
                        BigDecimal availability = parseDecimal(map.get("metric.availabilityRate"));
                        BigDecimal fault = parseDecimal(map.get("metric.faultRate"));
                        BigDecimal oee = parseDecimal(map.get("metric.oee"));
                        long updatedAt = parseLong(map.get("updatedAt"), 0L);
                        result.put(deviceId, new RealtimeMetricSnapshot(uptime, performance, availability, fault, oee, updatedAt));
                    } catch (Exception e) {
                        log.warn("批量读取实时指标解析失败: deviceId={}, key={}", deviceId, keys.get(i), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量读取实时指标失败: factoryId={}, deviceCount={}", factoryId, deviceIds.size(), e);
            // 如果批量读取失败，降级为单个读取
            for (Long deviceId : deviceIds) {
                Optional<RealtimeMetricSnapshot> snapOpt = getDeviceRealtimeMetrics(factoryId, deviceId);
                snapOpt.ifPresent(snap -> result.put(deviceId, snap));
            }
        }

        return result;
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
    
    // ==================== 内部类 ====================
    
    /**
     * 实时计算数据封装类
     */
    private static class RealtimeCalculationData {
        private final Long factoryId;
        private final Long deviceId;
        private final long nowMs;
        private final long shiftStartMillis;
        private final long shiftEndMillis;
        private final long shiftDurationMillis;
        private final long plannedDowntime;
        private final long plannedRuntimeMillis;
        private final StateDurations stateDurations;
        private final long actualRuntimeMillis;
        private final long actualOutput;
        private final long theoreticalCycle;
        private final long elapsedCalendarMillis;
        
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
     * 状态持续时间封装类
     */
    private static class StateDurations {
        private final long workingMillis;
        private final long standbyMillis;
        private final long faultMillis;
        private final long shutdownMillis;
        
        public StateDurations(long workingMillis, long standbyMillis, long faultMillis, long shutdownMillis) {
            this.workingMillis = workingMillis;
            this.standbyMillis = standbyMillis;
            this.faultMillis = faultMillis;
            this.shutdownMillis = shutdownMillis;
        }
        
        public long getWorkingMillis() { return workingMillis; }
        public long getStandbyMillis() { return standbyMillis; }
        public long getFaultMillis() { return faultMillis; }
        public long getShutdownMillis() { return shutdownMillis; }
        
        /**
         * 计算非计划停机时长（毫秒）
         * 非计划停机 = 待机 + 故障 + 关机
         */
        public long getUnplannedDowntimeMillis() {
            return standbyMillis + faultMillis + shutdownMillis;
        }
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

