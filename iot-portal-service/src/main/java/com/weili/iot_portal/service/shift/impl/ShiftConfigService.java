package com.weili.iot_portal.service.shift.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.repository.device.DeviceShiftConfigRepository;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.model.ShiftInfo;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;
import com.weili.iot_portal.service.shift.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 班次配置服务
 * 统一管理班次配置的查询和计算逻辑
 */
@Service
@RequiredArgsConstructor
public class ShiftConfigService implements IShiftConfigService {

    private final DeviceShiftConfigRepository deviceShiftConfigRepository;
    private final DeviceFactoryValidator deviceFactoryValidator;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 获取设备在当前时间的生效班次配置（带工厂验证）
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
        return configOpt.orElseThrow(() -> new ServiceException(
                ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                "设备未配置班次信息，请先配置班次"));
    }

    /**
     * 根据时间点确定当前班次信息（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息
     */
    public ShiftInfo getCurrentShift(String factoryId, String deviceId, long timestamp) {
        DeviceShiftConfigDO config = getCurrentConfiguration(factoryId, deviceId, timestamp);
        return findShiftByTime(config, timestamp);
    }

    /**
     * 计算班次的时间范围（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次时间范围
     */
    public ShiftTimeRange calculateShiftRange(String factoryId, String deviceId, long timestamp) {
        DeviceShiftConfigDO config = getCurrentConfiguration(factoryId, deviceId, timestamp);
        ShiftInfo shift = findShiftByTime(config, timestamp);

        LocalDateTime baseTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());

        LocalDate baseDate = baseTime.toLocalDate();
        LocalTime startTime = LocalTime.parse(shift.getStartTime(), TIME_FORMATTER);
        LocalTime endTime = LocalTime.parse(shift.getEndTime(), TIME_FORMATTER);

        LocalDateTime shiftStart;
        LocalDateTime shiftEnd;

        LocalTime currentTime = baseTime.toLocalTime();

        if (Boolean.TRUE.equals(shift.getCrossDay())) {
            // 跨天班次处理
            if (currentTime.isBefore(startTime)) {
                // 当前时间在跨天班次的后半段（例如：00:00-08:00，当前是02:00）
                shiftStart = baseDate.minusDays(1).atTime(startTime);
                shiftEnd = baseDate.atTime(endTime);
            } else {
                // 当前时间在跨天班次的前半段（例如：20:00-次日08:00，当前是22:00）
                shiftStart = baseDate.atTime(startTime);
                shiftEnd = baseDate.plusDays(1).atTime(endTime);
            }
        } else {
            // 不跨天班次
            if (currentTime.isBefore(startTime)) {
                // 当前时间在班次开始前，说明是前一个班次
                // 这种情况理论上不应该发生，因为应该找到正确的班次
                shiftStart = baseDate.minusDays(1).atTime(startTime);
                shiftEnd = baseDate.atTime(endTime);
            } else {
                shiftStart = baseDate.atTime(startTime);
                shiftEnd = baseDate.atTime(endTime);
            }
        }

        long startTs = shiftStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endTs = shiftEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        return ShiftTimeRange.builder()
                .shiftCode(shift.getCode())
                .shiftName(shift.getName())
                .startTs(startTs)
                .endTs(endTs)
                .durationMs(endTs - startTs)
                .build();
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
     * 根据时间点从配置中找到对应的班次
     *
     * @param config    班次配置
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息
     */
    private ShiftInfo findShiftByTime(DeviceShiftConfigDO config, long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());
        LocalTime currentTime = dateTime.toLocalTime();

        List<DeviceShiftConfigDO.ShiftDefinition> shifts = config.getShifts();
        if (shifts == null || shifts.isEmpty()) {
            throw new ServiceException(
                    ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                    "班次配置为空");
        }

        // 遍历所有班次，找到包含当前时间的班次
        for (DeviceShiftConfigDO.ShiftDefinition shiftDef : shifts) {
            LocalTime startTime = LocalTime.parse(shiftDef.getStartTime(), TIME_FORMATTER);
            LocalTime endTime = LocalTime.parse(shiftDef.getEndTime(), TIME_FORMATTER);

            boolean isInShift;
            if (Boolean.TRUE.equals(shiftDef.getCrossDay())) {
                // 跨天班次：例如 20:00-08:00
                isInShift = currentTime.isAfter(startTime) || currentTime.isBefore(endTime);
            } else {
                // 不跨天班次：例如 08:00-20:00
                isInShift = !currentTime.isBefore(startTime) && currentTime.isBefore(endTime);
            }

            if (isInShift) {
                return ShiftInfo.builder()
                        .code(shiftDef.getCode())
                        .name(shiftDef.getName())
                        .startTime(shiftDef.getStartTime())
                        .endTime(shiftDef.getEndTime())
                        .durationHours(shiftDef.getDurationHours())
                        .crossDay(shiftDef.getCrossDay())
                        .build();
            }
        }

        // 如果没有找到，可能是时间点在班次间隙，返回第一个班次（作为默认）
        // 或者抛出异常，根据业务需求决定
        DeviceShiftConfigDO.ShiftDefinition firstShift = shifts.get(0);
        return ShiftInfo.builder()
                .code(firstShift.getCode())
                .name(firstShift.getName())
                .startTime(firstShift.getStartTime())
                .endTime(firstShift.getEndTime())
                .durationHours(firstShift.getDurationHours())
                .crossDay(firstShift.getCrossDay())
                .build();
    }
}

