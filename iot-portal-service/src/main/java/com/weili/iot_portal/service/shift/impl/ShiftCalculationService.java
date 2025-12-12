package com.weili.iot_portal.service.shift.impl;

import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.service.shift.DeviceFactoryValidator;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.ShiftConstants;
import com.weili.iot_portal.service.shift.model.ShiftInfo;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.weili.iot_portal.common.exception.IotPortalErrorCode.SHIFT_CONFIG_EMPTY;

/**
 * 班次计算服务实现
 * 负责班次相关的计算逻辑，如根据时间点计算班次信息、时间范围等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftCalculationService implements IShiftCalculationService {

    private final IShiftConfigService shiftConfigService;
    private final DeviceFactoryValidator deviceFactoryValidator;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(ShiftConstants.TIME_FORMAT);

    /**
     * 根据时间点确定当前班次信息（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息
     */
    @Override
    public ShiftInfo getCurrentShift(String factoryId, String deviceId, long timestamp) {
        DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(factoryId, deviceId, timestamp);
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
    @Override
    public ShiftTimeRange calculateShiftRange(String factoryId, String deviceId, long timestamp) {
        DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(factoryId, deviceId, timestamp);
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
     * 获取时间戳对应的班次日期和编码（带工厂验证）
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次日期和编码
     */
    @Override
    public ShiftDateAndCode getShiftDateAndCode(String factoryId, String deviceId, long timestamp) {
        ShiftTimeRange range = calculateShiftRange(factoryId, deviceId, timestamp);
        LocalDate shiftDate = Instant.ofEpochMilli(range.getStartTs())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return new ShiftDateAndCode(shiftDate, range.getShiftCode());
    }

    /**
     * 检查时间范围是否跨班（带工厂验证）
     * <p>
     * 判断逻辑：
     * 1. 获取开始时间所在的班次时间范围
     * 2. 检查结束时间是否超出了开始时间所在班次的范围
     * 3. 如果结束时间超出范围，说明跨班
     * <p>
     * 示例：
     * - 开始时间：2025-12-11 20:00:00（晚班，范围：2025-12-11 20:00:00 ~ 2025-12-12 08:00:00）
     * - 结束时间：2025-12-12 08:00:00（在范围内）→ 不跨班
     * - 结束时间：2025-12-12 10:00:00（超出范围）→ 跨班
     * - 结束时间：2025-12-13 08:00:00（超出范围）→ 跨班
     * </p>
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param startTs 开始时间戳（毫秒）
     * @param endTs 结束时间戳（毫秒）
     * @return true表示跨班，false表示不跨班
     */
    @Override
    public boolean checkIfCrossesShift(String factoryId, String deviceId, Long startTs, Long endTs) {
        if (startTs == null || endTs == null) {
            return false;
        }
        
        // 结束时间必须大于开始时间
        if (endTs <= startTs) {
            return false;
        }
        
        try {
            // 获取开始时间所在的班次时间范围
            ShiftTimeRange startShiftRange = calculateShiftRange(factoryId, deviceId, startTs);
            
            // 检查结束时间是否超出了开始时间所在班次的范围
            // 如果结束时间 > 班次结束时间，说明跨班
            return endTs > startShiftRange.getEndTs();
        } catch (Exception e) {
            // 班次计算失败时，保守处理，认为不跨班
            log.debug("[ShiftCalculationService] 检查跨班失败，默认不跨班: deviceId={}, startTs={}, endTs={}, error={}",
                    deviceId, startTs, endTs, e.getMessage());
            return false;
        }
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
        // 配置服务应该确保 shifts 已构建，如果为空则抛出异常
        if (shifts == null || shifts.isEmpty()) {
            throw new IotPortalException(SHIFT_CONFIG_EMPTY);
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

