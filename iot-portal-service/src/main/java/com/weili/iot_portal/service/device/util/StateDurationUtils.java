package com.weili.iot_portal.service.device.util;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态时长工具类
 * <p>
 * 提供从设备状态汇总数据中提取状态时长和计算非计划停机时长的公共方法
 * </p>
 */
public class StateDurationUtils {

    /**
     * 状态类型数量（WORKING, STANDBY, FAULT, SHUTDOWN, UNKNOWN）
     */
    private static final int STATE_TYPE_COUNT = 5;

    /**
     * 状态类型数组索引
     */
    private static final int INDEX_WORKING = 0;
    private static final int INDEX_STANDBY = 1;
    private static final int INDEX_FAULT = 2;
    private static final int INDEX_SHUTDOWN = 3;
    private static final int INDEX_UNKNOWN = 4;

    /**
     * 空状态时长对象（避免重复创建）
     */
    private static final StateDurations EMPTY_DURATIONS = new StateDurations(0L, 0L, 0L, 0L, 0L);

    /**
     * 三参数函数接口（用于班次日期计算）
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }

    /**
     * 状态时长封装类
     */
    public static class StateDurations {
        private final long workingMillis;
        private final long standbyMillis;
        private final long faultMillis;
        private final long shutdownMillis;
        private final long unknownMillis;

        public StateDurations(long workingMillis, long standbyMillis, long faultMillis, 
                             long shutdownMillis, long unknownMillis) {
            this.workingMillis = workingMillis;
            this.standbyMillis = standbyMillis;
            this.faultMillis = faultMillis;
            this.shutdownMillis = shutdownMillis;
            this.unknownMillis = unknownMillis;
        }

        public long getWorkingMillis() {
            return workingMillis;
        }

        public long getStandbyMillis() {
            return standbyMillis;
        }

        public long getFaultMillis() {
            return faultMillis;
        }

        public long getShutdownMillis() {
            return shutdownMillis;
        }

        public long getUnknownMillis() {
            return unknownMillis;
        }

        /**
         * 计算非计划停机时长（毫秒）
         * 非计划停机 = 待机 + 故障 + 关机
         */
        public long getUnplannedDowntimeMillis() {
            return standbyMillis + faultMillis + shutdownMillis;
        }

        /**
         * 累加另一个状态时长对象
         */
        public StateDurations add(StateDurations other) {
            if (other == null) {
                return this;
            }
            return new StateDurations(
                    this.workingMillis + other.workingMillis,
                    this.standbyMillis + other.standbyMillis,
                    this.faultMillis + other.faultMillis,
                    this.shutdownMillis + other.shutdownMillis,
                    this.unknownMillis + other.unknownMillis
            );
        }
    }

    /**
     * 从设备状态汇总数据中提取各状态的时长
     * <p>
     * 注意：DeviceStateSummaryDO 的 *DurationS 字段实际存储的是毫秒值
     * </p>
     */
    public static StateDurations extractStateDurations(DeviceStateSummaryDO summary) {
        if (summary == null) {
            return EMPTY_DURATIONS;
        }

        return new StateDurations(
                getSafe(summary.getWorkingDurationS()),
                getSafe(summary.getStandbyDurationS()),
                getSafe(summary.getFaultDurationS()),
                getSafe(summary.getShutdownDurationS()),
                getSafe(summary.getUnknownDurationS())
        );
    }

    /**
     * 统计历史班次日期范围内各状态的时长汇总（优先使用汇总表，提升查询速度）
     * <p>
     * 优先从 device_state_summary 表查询，缺失的日期再从 device_state_record 表汇总
     * </p>
     *
     * @param deviceId 设备ID
     * @param shiftDates 班次日期列表（已转换好的班次日期）
     * @param summaryRepository 状态汇总仓储
     * @param recordRepository 状态记录仓储
     */
    public static StateDurations sumStateDurationsFromShiftDates(
            Long deviceId,
            List<LocalDate> shiftDates,
            DeviceStateSummaryRepository summaryRepository,
            DeviceStateRecordRepository recordRepository) {
        
        if (shiftDates == null || shiftDates.isEmpty()) {
            return EMPTY_DURATIONS;
        }

        // 去重并排序
        List<LocalDate> uniqueDates = shiftDates.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        LocalDate startDate = uniqueDates.get(0);
        LocalDate endDate = uniqueDates.get(uniqueDates.size() - 1);

        // 1. 优先从汇总表查询
        List<DeviceStateSummaryDO> summaries = summaryRepository.selectByShiftDateRange(
                deviceId, startDate, endDate);

        // 按班次日期分组
        Map<LocalDate, List<DeviceStateSummaryDO>> summariesByDate = summaries.stream()
                .collect(Collectors.groupingBy(DeviceStateSummaryDO::getSummaryDate));

        // 2. 找出缺失的日期
        Set<LocalDate> missingDates = new HashSet<>(uniqueDates);
        missingDates.removeAll(summariesByDate.keySet());

        // 3. 汇总各状态时长
        StateDurations result = EMPTY_DURATIONS;

        // 3.1 累加汇总表中的数据
        for (DeviceStateSummaryDO summary : summaries) {
            result = result.add(extractStateDurations(summary));
        }

        // 3.2 对于缺失的日期，从记录表汇总
        if (!missingDates.isEmpty()) {
            LocalDate missingStartDate = missingDates.stream().min(LocalDate::compareTo).orElse(startDate);
            LocalDate missingEndDate = missingDates.stream().max(LocalDate::compareTo).orElse(endDate);
            
            List<DeviceStateRecordDO> missingRecords = recordRepository.selectByShiftDateRange(
                    deviceId, missingStartDate, missingEndDate);

            List<DeviceStateRecordDO> filteredRecords = missingRecords.stream()
                    .filter(record -> record.getShiftDate() != null && missingDates.contains(record.getShiftDate()))
                    .collect(Collectors.toList());

            if (!filteredRecords.isEmpty()) {
                // 按班次日期汇总（历史班次，所有状态都已结束）
                StateDurations recordDurations = sumStateDurationsFromRecords(filteredRecords);
                result = result.add(recordDurations);
            }
        }

        return result;
    }

    /**
     * 统计今天（包括正在进行中的）各状态的时长汇总
     * <p>
     * 自动根据当前时间计算班次日期，从 device_state_record 表查询并汇总
     * 对于正在进行中的状态（endTs == null），使用当前时间作为结束时间
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param currentTime 当前时间（毫秒）
     * @param shiftDateCalculator 班次日期计算函数 (factoryId, deviceId, timestamp) -> shiftDate
     * @param recordRepository 状态记录仓储
     */
    public static StateDurations sumStateDurationsForToday(
            Long factoryId,
            Long deviceId,
            long currentTime,
            TriFunction<Long, Long, Long, LocalDate> shiftDateCalculator,
            DeviceStateRecordRepository recordRepository) {
        
        LocalDate shiftDate = shiftDateCalculator.apply(factoryId, deviceId, currentTime);
        return sumStateDurationsForToday(deviceId, shiftDate, currentTime, recordRepository);
    }

    /**
     * 统计今天（包括正在进行中的）各状态的时长汇总
     * <p>
     * 从 device_state_record 表查询指定班次日期的状态记录并汇总
     * 对于正在进行中的状态（endTs == null），使用当前时间作为结束时间
     * </p>
     *
     * @param deviceId 设备ID
     * @param shiftDate 班次日期（已计算好的班次日期）
     * @param currentTime 当前时间（毫秒），用于正在进行中的状态
     * @param recordRepository 状态记录仓储
     */
    public static StateDurations sumStateDurationsForToday(
            Long deviceId,
            LocalDate shiftDate,
            long currentTime,
            DeviceStateRecordRepository recordRepository) {
        
        if (shiftDate == null) {
            return EMPTY_DURATIONS;
        }

        List<DeviceStateRecordDO> stateRecords = recordRepository.selectByShiftDateRange(
                deviceId, shiftDate, shiftDate);

        if (stateRecords == null || stateRecords.isEmpty()) {
            return EMPTY_DURATIONS;
        }

        // 按班次日期汇总（包括正在进行中的状态）
        return sumStateDurationsFromRecords(stateRecords, currentTime);
    }

    /**
     * 从状态记录列表中汇总各状态的时长（历史班次，所有状态都已结束）
     * <p>
     * 适用于历史班次统计，所有状态记录都已结束（endTs != null）
     * 直接使用记录的 durationS 字段或根据 startTs 和 endTs 计算时长
     * </p>
     *
     * @param stateRecords 状态记录列表（必须属于同一台设备）
     */
    public static StateDurations sumStateDurationsFromRecords(
            List<DeviceStateRecordDO> stateRecords) {
        return sumStateDurationsFromRecords(stateRecords, null);
    }

    /**
     * 从状态记录列表中汇总各状态的时长（包括正在进行中的状态）
     * <p>
     * 适用于当前班次统计，可能包含正在进行中的状态（endTs == null）
     * 对于正在进行中的状态，使用 currentTime 作为结束时间计算时长
     * </p>
     *
     * @param stateRecords 状态记录列表（必须属于同一台设备）
     * @param currentTime 当前时间（毫秒），用于正在进行中的状态；null 表示历史班次（所有状态都已结束）
     */
    public static StateDurations sumStateDurationsFromRecords(
            List<DeviceStateRecordDO> stateRecords,
            Long currentTime) {
        
        if (stateRecords == null || stateRecords.isEmpty()) {
            return EMPTY_DURATIONS;
        }

        // 验证所有记录是否属于同一台设备
        validateSameDevice(stateRecords);

        // 使用数组累加，避免多次 switch 判断
        long[] durations = new long[STATE_TYPE_COUNT];

        for (DeviceStateRecordDO record : stateRecords) {
            Integer stateCode = record.getStateCode();
            if (stateCode == null) {
                continue;
            }

            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(stateCode);
            long durationMillis = calculateRecordDuration(record, currentTime);
            
            if (durationMillis <= 0) {
                continue;
            }

            // 按状态类型累加时长（使用枚举值映射，避免字符串比较）
            int index = getStateIndex(stateEnum);
            durations[index] += durationMillis;
        }

        return new StateDurations(
                durations[INDEX_WORKING],
                durations[INDEX_STANDBY],
                durations[INDEX_FAULT],
                durations[INDEX_SHUTDOWN],
                durations[INDEX_UNKNOWN]
        );
    }

    /**
     * 验证所有记录是否属于同一台设备
     *
     * @param stateRecords 状态记录列表
     * @throws IllegalArgumentException 如果记录列表包含多台设备的记录或缺少设备ID
     */
    private static void validateSameDevice(List<DeviceStateRecordDO> stateRecords) {
        Long expectedDeviceId = null;
        for (DeviceStateRecordDO record : stateRecords) {
            Long deviceId = record.getDeviceInfoId();
            if (deviceId == null) {
                throw new IllegalArgumentException("状态记录缺少设备ID（deviceInfoId）");
            }
            if (expectedDeviceId == null) {
                expectedDeviceId = deviceId;
            } else if (!expectedDeviceId.equals(deviceId)) {
                throw new IllegalArgumentException(
                        String.format("状态记录列表包含多台设备的记录：设备ID %d 和 %d", expectedDeviceId, deviceId));
            }
        }
    }

    /**
     * 获取状态类型对应的数组索引
     *
     * @param stateEnum 状态枚举
     * @return 数组索引
     */
    private static int getStateIndex(DeviceStateEnum stateEnum) {
        if (stateEnum == null) {
            return INDEX_UNKNOWN;
        }
        switch (stateEnum) {
            case WORKING:
                return INDEX_WORKING;
            case STANDBY:
                return INDEX_STANDBY;
            case FAULT:
                return INDEX_FAULT;
            case SHUTDOWN:
                return INDEX_SHUTDOWN;
            case UNKNOWN:
            default:
                return INDEX_UNKNOWN;
        }
    }

    /**
     * 计算状态记录的持续时间
     * <p>
     * 对于已结束的状态，优先使用 durationS 字段，如果为 null 则根据 startTs 和 endTs 计算
     * 对于正在进行中的状态（endTs == null），如果 currentTime 不为 null，使用 currentTime 作为结束时间
     * </p>
     *
     * @param record 状态记录
     * @param currentTime 当前时间（毫秒），null 表示历史班次（所有状态都已结束）
     * @return 持续时间（毫秒）
     */
    private static long calculateRecordDuration(DeviceStateRecordDO record, Long currentTime) {
        Long endTs = record.getEndTs();
        
        if (endTs == null) {
            // 正在进行中的状态：如果提供了 currentTime，使用它作为结束时间
            if (currentTime != null) {
                Long startTs = record.getStartTs();
                if (startTs == null) {
                    return 0L;
                }
                return currentTime - startTs;
            }
            // 如果没有提供 currentTime，说明是历史班次，不应该有未结束的状态
            return 0L;
        }
        
        // 已结束的状态：优先使用 durationS 字段
        if (record.getDurationS() != null) {
            return record.getDurationS();
        }
        
        // 如果 durationS 为 null，根据 startTs 和 endTs 计算
        Long startTs = record.getStartTs();
        if (startTs == null) {
            return 0L;
        }
        
        return endTs - startTs;
    }

    /**
     * 计算已过日历时长（从班次日期第一个班次开始时间到当前时间）
     * <p>
     * 用于工厂级实时指标权重计算，计算从班次日期第一个班次开始时间到当前时间的"已过日历时长"（秒）。
     * 这与设备级实时指标计算保持一致，使用已过时长作为权重。
     * </p>
     *
     * @param shiftDate 班次日期
     * @param firstShiftStartTime 第一个班次的开始时间（HH:mm:ss格式字符串）
     * @param firstShiftEndTime 第一个班次的结束时间（HH:mm:ss格式字符串）
     * @param isCrossDay 第一个班次是否跨天
     * @param calcTimeMs 当前时间戳（毫秒）
     * @return 已过日历时长（秒），如果计算失败返回0
     */
    public static long calculateElapsedCalendarSeconds(
            LocalDate shiftDate,
            String firstShiftStartTime,
            String firstShiftEndTime,
            Boolean isCrossDay,
            long calcTimeMs) {
        if (shiftDate == null || firstShiftStartTime == null) {
            return 0;
        }

        try {
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            java.time.LocalTime startTime = java.time.LocalTime.parse(firstShiftStartTime, timeFormatter);

            // 计算班次日期第一个班次的开始时间戳
            java.time.LocalDateTime firstShiftStartDateTime = shiftDate.atTime(startTime);

            // 如果第一个班次是跨天班次，需要检查是否需要调整日期
            if (Boolean.TRUE.equals(isCrossDay) && firstShiftEndTime != null) {
                java.time.LocalTime endTime = java.time.LocalTime.parse(firstShiftEndTime, timeFormatter);
                // 跨天班次：如果开始时间小于结束时间（如00:00 < 08:00），说明是从前一天开始的跨天班次
                if (startTime.isBefore(endTime)) {
                    firstShiftStartDateTime = shiftDate.minusDays(1).atTime(startTime);
                }
                // 否则，使用 shiftDate 当天的 startTime（跨天班次从当天开始）
            }

            long dayStartMillis = firstShiftStartDateTime.atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli();

            // 计算已过日历时长（从班次日期第一个班次开始时间到当前时间）
            return Math.max(1, (calcTimeMs - dayStartMillis) / 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 安全获取整数值，null 时返回 0
     */
    private static long getSafe(Integer value) {
        return value != null ? value.longValue() : 0L;
    }
}
