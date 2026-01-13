package com.weili.iot_portal.service.shift.impl;

import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftDefinition;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftInfo;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.IShiftConfigService;
import com.weili.iot_portal.service.shift.ShiftConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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
    public ShiftInfo getCurrentShift(Long factoryId, Long deviceId, long timestamp) {
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
    public ShiftTimeRange calculateShiftRange(Long factoryId, Long deviceId, long timestamp) {
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

        // 计算班次日期：对于跨天班次，使用开始时间所在的日期；对于不跨天班次，使用开始时间所在的日期（与结束时间相同）
        LocalDate calculatedShiftDate = shiftStart.toLocalDate();

        return ShiftTimeRange.builder()
                .shiftCode(shift.getCode())
                .shiftName(shift.getName())
                .shiftDate(calculatedShiftDate)
                .startTs(startTs)
                .endTs(endTs)
                .durationMs(endTs - startTs)
                .build();
    }

    /**
     * 获取时间戳对应的班次日期和编码（带工厂验证）
     * <p>
     * 班次配置获取：
     * - 优先从数据库查询设备的班次配置
     * - 如果数据库中没有配置，使用默认班次配置（默认2班制，可通过配置项 shift.default.mode 修改）
     * </p>
     * <p>
     * 班次日期计算规则：
     * 1. 对于跨天班次（如三班制第三班：次日0:00-次日8:00，两班制第二班：20:00-次日8:00）：
     *    - 如果时间戳在班次的后半段（次日的部分），班次日期应该是前一日
     *    - 例如：三班制第三班，时间戳是次日2:00，业务上属于"昨日"的第三班，shiftDate应该是昨日
     *    - 注意：如果传入的时间戳是 00:00:00（只有年月日，没有时分秒），对于跨天班次会被判断为后半段，属于前一天的班次
     * 2. 对于不跨天班次：
     *    - 班次日期就是时间戳对应的日期
     * </p>
     * <p>
     * <b>重要提示：</b>
     * - timestamp 应该包含完整的时分秒信息，以确保班次判断的准确性
     * - 如果传入的时间戳只有年月日（如 2025-01-01 00:00:00），对于跨天班次可能会被判断为前一天的班次
     * - 建议调用方确保传入的时间戳包含完整的时分秒信息
     * </p>
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒），建议包含完整的时分秒信息
     * @return 班次日期和编码
     */
    @Override
    public ShiftDateAndCode getShiftDateAndCode(Long factoryId, Long deviceId, long timestamp) {
        DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(factoryId, deviceId, timestamp);
        ShiftInfo shift = findShiftByTime(config, timestamp);
        
        LocalDateTime baseTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault());
        LocalDate baseDate = baseTime.toLocalDate();
        LocalTime currentTime = baseTime.toLocalTime();
        LocalTime startTime = LocalTime.parse(shift.getStartTime(), TIME_FORMATTER);
        
        LocalDate shiftDate;
        
        if (Boolean.TRUE.equals(shift.getCrossDay())) {
            // 跨天班次处理
            LocalTime endTime = LocalTime.parse(shift.getEndTime(), TIME_FORMATTER);
            
            // 判断时间戳在班次的哪个部分：
            // 1. 如果 currentTime < endTime，说明在班次的后半段（次日的部分）
            //    例如：三班制第三班 00:00-08:00，时间戳是次日02:00，属于前一天的班次
            //    注意：如果传入的是 00:00:00，也会被判断为后半段，属于前一天的班次
            // 2. 如果 currentTime >= startTime，说明在班次的前半段（当日的部分）
            //    例如：两班制第二班 20:00-次日08:00，时间戳是当日22:00，属于当天的班次
            if (currentTime.isBefore(endTime)) {
                // 当前时间在跨天班次的后半段（次日的部分）
                // 业务上属于前一天的班次
                shiftDate = baseDate.minusDays(1);
            } else if (!currentTime.isBefore(startTime)) {
                // 当前时间在跨天班次的前半段（当日的部分）
                // 业务上属于当天的班次
                shiftDate = baseDate;
            } else {
                // 边界情况：currentTime < startTime 且 currentTime >= endTime
                // 这种情况理论上不应该发生，因为 findShiftByTime 已经过滤了不在班次内的时间
                // 但为了安全，使用 baseDate（与不跨天班次的处理保持一致）
                log.warn("[ShiftCalculationService] 跨天班次边界情况: factoryId={}, deviceId={}, " +
                        "timestamp={}, currentTime={}, startTime={}, endTime={}, shiftCode={}",
                        factoryId, deviceId, timestamp, currentTime, startTime, endTime, shift.getCode());
                shiftDate = baseDate;
            }
        } else {
            // 不跨天班次：班次日期就是时间戳对应的日期
            shiftDate = baseDate;
        }
        
        return new ShiftDateAndCode(shiftDate, shift.getCode());
    }

    /**
     * 获取时间戳对应的班次日期（带工厂验证）
     * <p>
     * 根据当前时间戳和班次配置计算班次日期，考虑跨天班次的情况。
     * 例如：当前是2026-1-13 04:00，两班制第二班（20:00-次日08:00），班次日期应该是2026-1-12
     * </p>
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒），建议包含完整的时分秒信息
     * @return 班次日期
     */
    @Override
    public LocalDate getShiftDate(Long factoryId, Long deviceId, long timestamp) {
        return getShiftDateAndCode(factoryId, deviceId, timestamp).shiftDate();
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
    public boolean checkIfCrossesShift(Long factoryId, Long deviceId, Long startTs, Long endTs) {
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

        List<DeviceShiftDefinition> shifts = config.getShifts();
        // 配置服务应该确保 shifts 已构建，如果为空则抛出异常
        if (shifts == null || shifts.isEmpty()) {
            throw new IotPortalException(SHIFT_CONFIG_EMPTY);
        }

        // 遍历所有班次，找到包含当前时间的班次
        for (DeviceShiftDefinition shiftDef : shifts) {
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
        DeviceShiftDefinition firstShift = shifts.get(0);
        return ShiftInfo.builder()
                .code(firstShift.getCode())
                .name(firstShift.getName())
                .startTime(firstShift.getStartTime())
                .endTime(firstShift.getEndTime())
                .durationHours(firstShift.getDurationHours())
                .crossDay(firstShift.getCrossDay())
                .build();
    }

    /**
     * 计算前一个班次的时间范围
     * 根据当前时间点，计算前一个班次（刚结束的班次）的时间范围
     *
     * @param factoryId            工厂ID
     * @param deviceId             设备ID
     * @param statisticsTimeMillis 统计时间点（毫秒）
     * @return 前一个班次的时间范围，如果无法计算则返回null
     */
    @Override
    public ShiftTimeRange calculatePreviousShiftRange(
            Long factoryId,
            Long deviceId,
            long statisticsTimeMillis) {
        try {
            // 获取当前时间点的班次
            ShiftTimeRange currentShift = calculateShiftRange(
                    factoryId, deviceId, statisticsTimeMillis);

            // 计算前一个班次
            // 前一个班次的结束时间 = 当前班次的开始时间
            long previousShiftEndTs = currentShift.getStartTs();

            // 根据结束时间计算前一个班次的时间范围
            return calculateShiftRangeByEndTime(factoryId, deviceId, previousShiftEndTs);
        } catch (Exception e) {
            log.warn("计算前一个班次失败: factoryId={}, deviceId={}, error={}",
                    factoryId, deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 根据结束时间计算班次时间范围
     * 用于根据已知的班次结束时间，反推整个班次的时间范围
     *
     * @param factoryId  工厂ID
     * @param deviceId  设备ID
     * @param endTsMillis 班次结束时间戳（毫秒）
     * @return 班次时间范围，如果无法计算则返回null
     */
    @Override
    public ShiftTimeRange calculateShiftRangeByEndTime(
            Long factoryId,
            Long deviceId,
            long endTsMillis) {
        try {
            // 获取该时间点的班次配置
            DeviceShiftConfigDO config = shiftConfigService.getCurrentConfiguration(
                    factoryId, deviceId, endTsMillis);

            LocalDateTime endDateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(endTsMillis),
                    ZoneId.systemDefault());

            LocalDate shiftDate = endDateTime.toLocalDate();
            LocalTime endTime = endDateTime.toLocalTime();

            // 查找匹配的班次
            List<DeviceShiftDefinition> shifts = config.getShifts();
            if (shifts == null || shifts.isEmpty()) {
                log.warn("班次配置为空: factoryId={}, deviceId={}", factoryId, deviceId);
                return null;
            }

            for (DeviceShiftDefinition shift : shifts) {
                LocalTime shiftEndTime = LocalTime.parse(shift.getEndTime(), TIME_FORMATTER);

                // 检查是否匹配（考虑跨天情况）
                boolean matches = false;
                if (Boolean.TRUE.equals(shift.getCrossDay())) {
                    // 跨天班次：结束时间可能是当天的结束时间或次日的结束时间
                    matches = endTime.equals(shiftEndTime) ||
                            endTime.equals(shiftEndTime.minusHours(24));
                } else {
                    matches = endTime.equals(shiftEndTime);
                }

                if (matches) {
                    // 计算开始时间
                    LocalTime shiftStartTime = LocalTime.parse(shift.getStartTime(), TIME_FORMATTER);
                    LocalDateTime shiftStart;
                    LocalDate calculatedShiftDate;

                    if (Boolean.TRUE.equals(shift.getCrossDay())) {
                        // 跨天班次：开始时间是前一天
                        shiftStart = shiftDate.minusDays(1).atTime(shiftStartTime);
                        // 跨天班次的日期应该是开始时间所在的日期
                        calculatedShiftDate = shiftStart.toLocalDate();
                    } else {
                        shiftStart = shiftDate.atTime(shiftStartTime);
                        // 不跨天班次的日期就是结束时间所在的日期（与开始时间相同）
                        calculatedShiftDate = shiftDate;
                    }

                    long startTs = shiftStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

                    return ShiftTimeRange.builder()
                            .shiftCode(shift.getCode())
                            .shiftName(shift.getName())
                            .shiftDate(calculatedShiftDate)
                            .startTs(startTs)
                            .endTs(endTsMillis)
                            .durationMs(endTsMillis - startTs)
                            .build();
                }
            }

            log.warn("未找到匹配的班次: factoryId={}, deviceId={}, endTime={}",
                    factoryId, deviceId, endTime);
            return null;
        } catch (Exception e) {
            log.warn("根据结束时间计算班次范围失败: factoryId={}, deviceId={}, error={}",
                    factoryId, deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 计算并验证班次时间范围
     * 根据参考时间戳计算班次时间范围，并验证是否与预期的班次日期和编码匹配
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param referenceTimeMillis 参考时间戳（毫秒）
     * @param expectedShiftDate 预期的班次日期
     * @param expectedShiftCode 预期的班次编码
     * @return 班次时间范围，如果不匹配则返回empty
     */
    @Override
    public Optional<ShiftTimeRange> calculateAndValidateShiftRange(
            Long factoryId,
            Long deviceId,
            long referenceTimeMillis,
            LocalDate expectedShiftDate,
            Integer expectedShiftCode) {
        
        // 1. 根据参考时间戳计算班次时间范围
        ShiftTimeRange shiftRange = calculateShiftRange(factoryId, deviceId, referenceTimeMillis);
        
        if (shiftRange == null) {
            log.warn("无法计算班次时间范围: factoryId={}, deviceId={}, referenceTimeMillis={}",
                    factoryId, deviceId, referenceTimeMillis);
            return Optional.empty();
        }
        
        // 2. 验证计算出的班次日期和编码是否与预期匹配
        LocalDate calculatedShiftDate = shiftRange.getShiftDate();
        Integer calculatedShiftCode = shiftRange.getShiftCode();
        
        if (!expectedShiftDate.equals(calculatedShiftDate) || 
            !expectedShiftCode.equals(calculatedShiftCode)) {
            log.warn("班次日期或编码不匹配: factoryId={}, deviceId={}, " +
                    "expected=({}, {}), calculated=({}, {})",
                    factoryId, deviceId,
                    expectedShiftDate, expectedShiftCode,
                    calculatedShiftDate, calculatedShiftCode);
            return Optional.empty();
        }
        
        return Optional.of(shiftRange);
    }
}

