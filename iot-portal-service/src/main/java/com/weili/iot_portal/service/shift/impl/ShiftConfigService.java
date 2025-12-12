package com.weili.iot_portal.service.shift.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.repository.device.DeviceShiftConfigRepository;
import com.weili.iot_portal.service.shift.DeviceFactoryValidator;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.ShiftConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    public DeviceShiftConfigDO getCurrentConfiguration(String factoryId, String deviceId, long timestamp) {
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
            return config;
        }
        
        // 设备未配置班次，使用默认班次配置
        log.debug("[ShiftConfigService] 设备未配置班次信息，使用默认班次配置: deviceId={}, mode={}", 
                deviceId, defaultShiftMode);
        return createDefaultShiftConfig(defaultShiftMode);
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
    public List<DeviceShiftConfigDO> getConfigurationsInRange(String factoryId, String deviceId, long startTs, long endTs) {
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
        
        List<DeviceShiftConfigDO.ShiftDefinition> shifts = new ArrayList<>();
        
        if (mode == ShiftConstants.SHIFT_MODE_2) {
            // 2班制：早班8:00-20:00，晚班20:00-次日8:00
            DeviceShiftConfigDO.ShiftDefinition shift1 = new DeviceShiftConfigDO.ShiftDefinition();
            shift1.setCode(ShiftConstants.SHIFT_CODE_1);
            shift1.setName(ShiftConstants.SHIFT_NAME_2MODE_DAY);
            shift1.setStartTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_START);
            shift1.setEndTime(ShiftConstants.SHIFT_TIME_2MODE_DAY_END);
            shift1.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_2MODE);
            shift1.setCrossDay(false);
            shifts.add(shift1);
            
            DeviceShiftConfigDO.ShiftDefinition shift2 = new DeviceShiftConfigDO.ShiftDefinition();
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
            DeviceShiftConfigDO.ShiftDefinition shift1 = new DeviceShiftConfigDO.ShiftDefinition();
            shift1.setCode(ShiftConstants.SHIFT_CODE_1);
            shift1.setName(ShiftConstants.SHIFT_NAME_3MODE_FIRST);
            shift1.setStartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_START);
            shift1.setEndTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            shift1.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_3MODE);
            shift1.setCrossDay(false);
            shifts.add(shift1);
            
            DeviceShiftConfigDO.ShiftDefinition shift2 = new DeviceShiftConfigDO.ShiftDefinition();
            shift2.setCode(ShiftConstants.SHIFT_CODE_2);
            shift2.setName(ShiftConstants.SHIFT_NAME_3MODE_SECOND);
            shift2.setStartTime(ShiftConstants.SHIFT_TIME_3MODE_FIRST_END);
            shift2.setEndTime(ShiftConstants.SHIFT_TIME_3MODE_SECOND_END);
            shift2.setDurationHours(ShiftConstants.SHIFT_DURATION_HOURS_3MODE);
            shift2.setCrossDay(true);
            shifts.add(shift2);
            
            DeviceShiftConfigDO.ShiftDefinition shift3 = new DeviceShiftConfigDO.ShiftDefinition();
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
    private List<DeviceShiftConfigDO.ShiftDefinition> buildShiftsFromFields(DeviceShiftConfigDO config) {
        List<DeviceShiftConfigDO.ShiftDefinition> shifts = new ArrayList<>();
        Integer mode = config.getShiftMode();
        
        if (mode == null) {
            return shifts;
        }
        
        // 构建班次1
        if (config.getShift1Code() != null) {
            DeviceShiftConfigDO.ShiftDefinition shift1 = new DeviceShiftConfigDO.ShiftDefinition();
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
            DeviceShiftConfigDO.ShiftDefinition shift2 = new DeviceShiftConfigDO.ShiftDefinition();
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
            DeviceShiftConfigDO.ShiftDefinition shift3 = new DeviceShiftConfigDO.ShiftDefinition();
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

}

