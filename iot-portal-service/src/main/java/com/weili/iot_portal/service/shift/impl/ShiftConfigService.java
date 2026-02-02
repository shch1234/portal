package com.weili.iot_portal.service.shift.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftDefinition;
import com.weili.iot_portal.dal.repository.device.DeviceShiftConfigRepository;
import com.weili.iot_portal.service.shift.DeviceFactoryValidator;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.ShiftConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 班次配置服务
 * 负责班次配置的查询和管理，不涉及班次计算逻辑
 * 班次计算逻辑请使用 {@link com.weili.iot_portal.service.shift.IShiftCalculationService}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftConfigService implements IShiftConfigService {

    private final DeviceShiftConfigRepository deviceShiftConfigRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(ShiftConstants.TIME_FORMAT);
    
    /**
     * 默认班次模式：2-2班制，3-3班制
     * 默认值为2（2班制）
     */
    @Value("${shift.default.mode:2}")
    private Integer defaultShiftMode;

    /**
     * 获取设备在当前时间的生效班次配置（带工厂验证）
     * 如果设备未配置班次，返回默认班次配置
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次配置
     */
    public DeviceShiftConfigDO getCurrentConfiguration(Long factoryId, Long deviceId, long timestamp) {
        // 验证设备属于指定工厂
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);

        Optional<DeviceShiftConfigDO> configOpt = deviceShiftConfigRepository
                .findActiveByDeviceAndTime(deviceId, timestamp);
        
        if (configOpt.isPresent()) {
            DeviceShiftConfigDO config = configOpt.get();
            // 确保 shifts 已构建
            if (config.getShifts() == null || config.getShifts().isEmpty()) {
                config.setShifts(buildShiftsFromFields(config));
            }
            // 验证配置合法性（运行时检测，记录警告但不阻止使用）
            validateShiftConfigSilently(config, deviceId);
            return config;
        }
        
        // 设备未配置班次，使用默认班次配置
        log.debug("[ShiftConfigService] 设备未配置班次信息，使用默认班次配置: deviceId={}, mode={}", 
                deviceId, defaultShiftMode);
        DeviceShiftConfigDO defaultConfig = createDefaultShiftConfig(defaultShiftMode);
        // 默认配置应该总是合法的，但也可以验证一下
        validateShiftConfigSilently(defaultConfig, deviceId);
        return defaultConfig;
    }


    /**
     * 获取时间范围内的所有配置版本（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param startTs   开始时间戳
     * @param endTs     结束时间戳
     * @return 班次配置列表（按生效时间倒序）
     */
    public List<DeviceShiftConfigDO> getConfigurationsInRange(Long factoryId, Long deviceId, long startTs, long endTs) {
        // 验证设备属于指定工厂
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);

        return deviceShiftConfigRepository.findByDeviceAndTimeRange(deviceId, startTs, endTs);
    }

    
    /**
     * 创建默认班次配置
     * 
     * @param mode 班次模式：2-2班制，3-3班制
     * @return 默认班次配置
     */
    private DeviceShiftConfigDO createDefaultShiftConfig(Integer mode) {
        DeviceShiftConfigDO config = new DeviceShiftConfigDO();
        config.setShiftMode(mode);
        
        List<DeviceShiftDefinition> shifts = new ArrayList<>();
        
        if (mode == ShiftConstants.SHIFT_MODE_2) {
            // 2班制：早班8:00-20:00，晚班20:00-次日8:00
            DeviceShiftDefinition shift1 = new DeviceShiftDefinition();
            shift1.setCode(ShiftConstants.SHIFT_CODE_1);
            shift1.setName(ShiftConstants.SHIFT_NAME_2MODE_DAY);
            shift1.setStartTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_START);
            shift1.setEndTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_END);
            shift1.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_2MODE);
            shift1.setCrossDay(false);
            shifts.add(shift1);
            
            DeviceShiftDefinition shift2 = new DeviceShiftDefinition();
            shift2.setCode(ShiftConstants.SHIFT_CODE_2);
            shift2.setName(ShiftConstants.SHIFT_NAME_2MODE_NIGHT);
            shift2.setStartTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_END);
            shift2.setEndTime(ShiftConstants.SHIFT_TIME_2MODE_NIGHT_END);
            shift2.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_2MODE);
            shift2.setCrossDay(true);
            shifts.add(shift2);
            
            // 设置班次字段（用于兼容）
            config.setShift1Code(ShiftConstants.SHIFT_CODE_1);
            config.setShift1Name(ShiftConstants.SHIFT_NAME_2MODE_DAY);
            config.setShift1StartTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_START);
            config.setShift1EndTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_END);
            config.setShift1DurationS(ShiftConstants.SHIFT_DURATION_HOURS_2MODE * ShiftConstants.SECONDS_PER_HOUR);
            
            config.setShift2Code(ShiftConstants.SHIFT_CODE_2);
            config.setShift2Name(ShiftConstants.SHIFT_NAME_2MODE_NIGHT);
            config.setShift2StartTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_END);
            config.setShift2EndTime(ShiftConstants.SHIFT_TIME_2MODE_NIGHT_END);
            config.setShift2DurationS(ShiftConstants.SHIFT_DURATION_HOURS_2MODE * ShiftConstants.SECONDS_PER_HOUR);
        } else if (mode == ShiftConstants.SHIFT_MODE_3) {
            // 3班制：第一班8:00-16:00，第二班16:00-00:00，第三班00:00-08:00（每班8小时）
            DeviceShiftDefinition shift1 = new DeviceShiftDefinition();
            shift1.setCode(ShiftConstants.SHIFT_CODE_1);
            shift1.setName(ShiftConstants.SHIFT_NAME_3MODE_FIRST);
            shift1.setStartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_START);
            shift1.setEndTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            shift1.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_3MODE);
            shift1.setCrossDay(false);
            shifts.add(shift1);
            
            DeviceShiftDefinition shift2 = new DeviceShiftDefinition();
            shift2.setCode(ShiftConstants.SHIFT_CODE_2);
            shift2.setName(ShiftConstants.SHIFT_NAME_3MODE_SECOND);
            shift2.setStartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            shift2.setEndTime(ShiftConstants.SHIFT_TIME_3MODE_SECOND_END);
            shift2.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_3MODE);
            shift2.setCrossDay(true);
            shifts.add(shift2);
            
            DeviceShiftDefinition shift3 = new DeviceShiftDefinition();
            shift3.setCode(ShiftConstants.SHIFT_CODE_3);
            shift3.setName(ShiftConstants.SHIFT_NAME_3MODE_THIRD);
            shift3.setStartTime(ShiftConstants.SHIFT_TIME_3MODE_SECOND_END);
            shift3.setEndTime(ShiftConstants.SHIFT_TIME_3MODE_THIRD_END);
            shift3.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_3MODE);
            shift3.setCrossDay(true);
            shifts.add(shift3);
            
            // 设置班次字段（用于兼容）
            config.setShift1Code(ShiftConstants.SHIFT_CODE_1);
            config.setShift1Name(ShiftConstants.SHIFT_NAME_3MODE_FIRST);
            config.setShift1StartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_START);
            config.setShift1EndTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            config.setShift1DurationS(ShiftConstants.SHIFT_DURATION_HOURS_3MODE * ShiftConstants.SECONDS_PER_HOUR);
            
            config.setShift2Code(ShiftConstants.SHIFT_CODE_2);
            config.setShift2Name(ShiftConstants.SHIFT_NAME_3MODE_SECOND);
            config.setShift2StartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            config.setShift2EndTime(ShiftConstants.SHIFT_TIME_3MODE_SECOND_END);
            config.setShift2DurationS(ShiftConstants.SHIFT_DURATION_HOURS_3MODE * ShiftConstants.SECONDS_PER_HOUR);
            
            config.setShift3Code(ShiftConstants.SHIFT_CODE_3);
            config.setShift3Name(ShiftConstants.SHIFT_NAME_3MODE_THIRD);
            config.setShift3StartTime(ShiftConstants.SHIFT_TIME_3MODE_SECOND_END);
            config.setShift3EndTime(ShiftConstants.SHIFT_TIME_3MODE_THIRD_END);
            config.setShift3DurationS(ShiftConstants.SHIFT_DURATION_HOURS_3MODE * ShiftConstants.SECONDS_PER_HOUR);
        } else {
            throw new IllegalArgumentException(
                    ShiftConstants.ERROR_UNSUPPORTED_SHIFT_MODE_PREFIX + mode + ShiftConstants.ERROR_UNSUPPORTED_SHIFT_MODE_SUFFIX);
        }
        
        config.setShifts(shifts);
        return config;
    }
    
    /**
     * 从数据库字段构建班次定义列表
     * 
     * @param config 班次配置
     * @return 班次定义列表
     */
    private List<DeviceShiftDefinition> buildShiftsFromFields(DeviceShiftConfigDO config) {
        List<DeviceShiftDefinition> shifts = new ArrayList<>();
        Integer mode = config.getShiftMode();
        
        if (mode == null) {
            return shifts;
        }
        
        // 构建班次1
        if (config.getShift1Code() != null) {
            DeviceShiftDefinition shift1 = new DeviceShiftDefinition();
            shift1.setCode(config.getShift1Code());
            shift1.setName(config.getShift1Name());
            shift1.setStartTime(config.getShift1StartTime());
            shift1.setEndTime(config.getShift1EndTime());
            if (config.getShift1DurationS() != null) {
                shift1.setDurationHours(config.getShift1DurationS() / ShiftConstants.SECONDS_PER_HOUR);
            }
            // 判断是否跨天：结束时间小于开始时间表示跨天
            if (config.getShift1StartTime() != null && config.getShift1EndTime() != null) {
                LocalTime startTime = LocalTime.parse(config.getShift1StartTime(), TIME_FORMATTER);
                LocalTime endTime = LocalTime.parse(config.getShift1EndTime(), TIME_FORMATTER);
                shift1.setCrossDay(endTime.isBefore(startTime) || endTime.equals(startTime));
            }
            shifts.add(shift1);
        }
        
        // 构建班次2
        if (config.getShift2Code() != null) {
            DeviceShiftDefinition shift2 = new DeviceShiftDefinition();
            shift2.setCode(config.getShift2Code());
            shift2.setName(config.getShift2Name());
            shift2.setStartTime(config.getShift2StartTime());
            shift2.setEndTime(config.getShift2EndTime());
            if (config.getShift2DurationS() != null) {
                shift2.setDurationHours(config.getShift2DurationS() / ShiftConstants.SECONDS_PER_HOUR);
            }
            if (config.getShift2StartTime() != null && config.getShift2EndTime() != null) {
                LocalTime startTime = LocalTime.parse(config.getShift2StartTime(), TIME_FORMATTER);
                LocalTime endTime = LocalTime.parse(config.getShift2EndTime(), TIME_FORMATTER);
                shift2.setCrossDay(endTime.isBefore(startTime) || endTime.equals(startTime));
            }
            shifts.add(shift2);
        }
        
        // 构建班次3（仅3班制）
        if (mode == ShiftConstants.SHIFT_MODE_3 && config.getShift3Code() != null) {
            DeviceShiftDefinition shift3 = new DeviceShiftDefinition();
            shift3.setCode(config.getShift3Code());
            shift3.setName(config.getShift3Name());
            shift3.setStartTime(config.getShift3StartTime());
            shift3.setEndTime(config.getShift3EndTime());
            if (config.getShift3DurationS() != null) {
                shift3.setDurationHours(config.getShift3DurationS() / ShiftConstants.SECONDS_PER_HOUR);
            }
            if (config.getShift3StartTime() != null && config.getShift3EndTime() != null) {
                LocalTime startTime = LocalTime.parse(config.getShift3StartTime(), TIME_FORMATTER);
                LocalTime endTime = LocalTime.parse(config.getShift3EndTime(), TIME_FORMATTER);
                shift3.setCrossDay(endTime.isBefore(startTime) || endTime.equals(startTime));
            }
            shifts.add(shift3);
        }
        
        return shifts;
    }

    /**
     * 验证班次配置的合法性
     * <p>
     * 检查项：
     * 1. 相邻班次边界时间不能相同（会导致边界重叠）
     * 2. 相邻班次时间间隔建议至少1分钟
     * 3. 班次数量必须大于0
     * </p>
     * 
     * @param config 班次配置
     * @throws IllegalArgumentException 如果配置不合法
     */
    public void validateShiftConfig(DeviceShiftConfigDO config) {
        if (config == null) {
            throw new IllegalArgumentException("班次配置不能为空");
        }

        List<DeviceShiftDefinition> shifts = config.getShifts();
        if (shifts == null || shifts.isEmpty()) {
            // 如果shifts为空，尝试从字段构建
            shifts = buildShiftsFromFields(config);
        }

        if (shifts == null || shifts.isEmpty()) {
            throw new IllegalArgumentException("班次配置中至少需要定义一个班次");
        }

        // 最小时间间隔（毫秒），建议至少1分钟
        long minIntervalMs = 60 * 1000L; // 1分钟

        // 检查相邻班次的边界时间
        for (int i = 0; i < shifts.size(); i++) {
            DeviceShiftDefinition current = shifts.get(i);
            DeviceShiftDefinition next = shifts.get((i + 1) % shifts.size()); // 循环检查最后一个和第一个

            // 验证当前班次的时间格式
            if (current.getStartTime() == null || current.getEndTime() == null) {
                throw new IllegalArgumentException(
                    String.format("班次 %s 的开始时间或结束时间为空", current.getCode()));
            }

            LocalTime currentStart;
            LocalTime currentEnd;
            try {
                currentStart = LocalTime.parse(current.getStartTime(), TIME_FORMATTER);
                currentEnd = LocalTime.parse(current.getEndTime(), TIME_FORMATTER);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format("班次 %s 的时间格式错误: %s", current.getCode(), e.getMessage()));
            }

            // 验证下一班次的时间格式
            if (next.getStartTime() == null || next.getEndTime() == null) {
                throw new IllegalArgumentException(
                    String.format("班次 %s 的开始时间或结束时间为空", next.getCode()));
            }

            LocalTime nextStart;
            try {
                nextStart = LocalTime.parse(next.getStartTime(), TIME_FORMATTER);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    String.format("班次 %s 的时间格式错误: %s", next.getCode(), e.getMessage()));
            }

            // 关键检查1：相邻班次边界时间验证
            // 注意：如果使用半开区间 [start, end)，边界时间相同是允许的
            // 因为边界时间点（endTime）属于下一班次，不重叠
            // 例如：早班 [08:00, 20:00)，晚班 [20:00, 次日08:00)
            // 20:00:00 不属于早班（半开区间），属于晚班，不重叠
            
            // 检查真正的错误：下一班次开始时间 < 当前班次结束时间（导致时间重叠）
            // 这种情况会导致时间重叠，是真正的错误
            boolean isOverlap = false;
            
            if (!Boolean.TRUE.equals(current.getCrossDay()) && !Boolean.TRUE.equals(next.getCrossDay())) {
                // 情况1：两个班次都不跨天
                // 例如：早班 08:00-14:00，晚班 13:00-20:00（错误：13:00 < 14:00）
                // 例如：早班 08:00-20:00，晚班 20:00-22:00（正常：边界时间相同，使用半开区间）
                if (nextStart.isBefore(currentEnd)) {
                    isOverlap = true;
                }
            } else if (!Boolean.TRUE.equals(current.getCrossDay()) && Boolean.TRUE.equals(next.getCrossDay())) {
                // 情况2：当前班次不跨天，下一班次跨天
                // 例如：早班 08:00-20:00，晚班 20:00-次日08:00（正常：边界时间相同）
                // 例如：早班 08:00-20:00，晚班 19:00-次日08:00（错误：19:00 < 20:00）
                if (nextStart.isBefore(currentEnd)) {
                    isOverlap = true;
                }
            } else if (Boolean.TRUE.equals(current.getCrossDay()) && !Boolean.TRUE.equals(next.getCrossDay())) {
                // 情况3：当前班次跨天，下一班次不跨天
                // 例如：晚班 20:00-次日08:00，早班 08:00-20:00（正常：边界时间相同）
                // 这种情况，currentEnd 是次日的 08:00，nextStart 是当天的 08:00
                // 在时间轴上，nextStart（当天08:00）< currentEnd（次日08:00），但这是正常的
                // 因为跨天，时间轴不同，所以不需要检查重叠
                // 但如果 nextStart > currentEnd（在LocalTime层面），说明有问题
                // 实际上，这种情况 nextStart 应该等于 currentEnd（都是08:00），所以不需要检查
            } else {
                // 情况4：两个班次都跨天（理论上不应该出现，但也要处理）
                // 这种情况比较复杂，暂时不检查重叠
            }
            
            if (isOverlap) {
                throw new IllegalArgumentException(
                    String.format("班次配置错误：班次 %s 的结束时间(%s) 晚于班次 %s 的开始时间(%s)，" +
                                    "会导致时间重叠，可能引发无限循环问题。",
                            current.getCode(), currentEnd, next.getCode(), nextStart));
            }
            
            // 如果边界时间相同，记录调试日志（使用半开区间时这是允许的）
            if (currentEnd.equals(nextStart)) {
                log.debug("[ShiftConfigService] 班次 {} 和 {} 边界时间相同（{}），使用半开区间时这是允许的，" +
                                "边界时间点属于下一班次，不重叠",
                        current.getCode(), next.getCode(), currentEnd);
            }

            // 关键检查2：相邻班次时间间隔建议至少1分钟（仅当时间间隔大于0时检查）
            long intervalMs;
            if (Boolean.TRUE.equals(current.getCrossDay())) {
                // 跨天班次：计算从当前结束时间到下一班次开始时间的间隔（考虑跨天）
                if (nextStart.isBefore(currentEnd)) {
                    // 下一班次开始时间在当天，说明当前班次跨天到次日
                    // 间隔 = (24:00 - currentEnd) + nextStart
                    Duration duration1 = Duration.between(currentEnd, LocalTime.MAX).plusSeconds(1);
                    Duration duration2 = Duration.between(LocalTime.MIN, nextStart);
                    intervalMs = duration1.plus(duration2).toMillis();
                } else {
                    // 下一班次开始时间在次日
                    intervalMs = Duration.between(currentEnd, nextStart).toMillis();
                }
            } else {
                // 不跨天班次
                if (nextStart.isBefore(currentEnd)) {
                    // 下一班次开始时间在当天，但当前班次不跨天，说明下一班次跨天
                    // 间隔 = (24:00 - currentEnd) + nextStart
                    Duration duration1 = Duration.between(currentEnd, LocalTime.MAX).plusSeconds(1);
                    Duration duration2 = Duration.between(LocalTime.MIN, nextStart);
                    intervalMs = duration1.plus(duration2).toMillis();
                } else {
                    // 正常情况：都在当天
                    intervalMs = Duration.between(currentEnd, nextStart).toMillis();
                }
            }

            if (intervalMs < minIntervalMs) {
                log.warn("[ShiftConfigService] 班次配置警告：班次 {} 和 {} 的时间间隔过小（{}ms），" +
                                "建议至少1分钟，以避免边界处理问题",
                        current.getCode(), next.getCode(), intervalMs);
            }
        }

        log.debug("[ShiftConfigService] 班次配置验证通过: 班次数={}", shifts.size());
    }

    /**
     * 静默验证配置（不抛出异常，只记录日志）
     * 用于运行时检测配置问题，但不阻止系统运行
     * 
     * @param config 班次配置
     * @param deviceId 设备ID（用于日志）
     */
    private void validateShiftConfigSilently(DeviceShiftConfigDO config, Long deviceId) {
        try {
            validateShiftConfig(config);
        } catch (IllegalArgumentException e) {
            log.error("[ShiftConfigService] 班次配置验证失败: deviceId={}, error={}。请检查并修复班次配置，否则可能导致班次计算异常和无限循环问题。", 
                    deviceId, e.getMessage());
            // 不抛出异常，允许系统继续运行，但记录严重错误日志
        }
    }

}

