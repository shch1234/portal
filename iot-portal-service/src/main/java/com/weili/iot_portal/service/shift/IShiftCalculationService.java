package com.weili.iot_portal.service.shift;

import com.weili.iot_portal.service.shift.model.ShiftInfo;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;

/**
 * 班次计算服务接口
 * 负责班次相关的计算逻辑，如根据时间点计算班次信息、时间范围等
 */
public interface IShiftCalculationService {

    /**
     * 根据时间点确定当前班次信息（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息
     */
    ShiftInfo getCurrentShift(String factoryId, String deviceId, long timestamp);

    /**
     * 计算班次的时间范围（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次时间范围
     */
    ShiftTimeRange calculateShiftRange(String factoryId, String deviceId, long timestamp);

    /**
     * 获取时间戳对应的班次日期和编码（带工厂验证）
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次日期和编码
     */
    ShiftDateAndCode getShiftDateAndCode(String factoryId, String deviceId, long timestamp);

    /**
     * 检查时间范围是否跨班（带工厂验证）
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param startTs 开始时间戳（毫秒）
     * @param endTs 结束时间戳（毫秒）
     * @return true表示跨班，false表示不跨班
     */
    boolean checkIfCrossesShift(String factoryId, String deviceId, Long startTs, Long endTs);

    /**
     * 计算前一个班次的时间范围
     * 根据当前时间点，计算前一个班次（刚结束的班次）的时间范围
     *
     * @param factoryId            工厂ID
     * @param deviceId             设备ID
     * @param statisticsTimeSeconds 统计时间点（秒）
     * @return 前一个班次的时间范围，如果无法计算则返回null
     */
    ShiftTimeRange calculatePreviousShiftRange(
            String factoryId,
            String deviceId,
            long statisticsTimeSeconds);

    /**
     * 根据结束时间计算班次时间范围
     * 用于根据已知的班次结束时间，反推整个班次的时间范围
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param endTsMillis 班次结束时间戳（毫秒）
     * @return 班次时间范围，如果无法计算则返回null
     */
    ShiftTimeRange calculateShiftRangeByEndTime(
            String factoryId,
            String deviceId,
            long endTsMillis);
}

