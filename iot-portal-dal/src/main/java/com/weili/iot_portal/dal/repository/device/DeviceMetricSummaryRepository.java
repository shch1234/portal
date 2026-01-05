package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 设备班次指标仓储
 */
public interface DeviceMetricSummaryRepository {

    PageResult<DeviceMetricSummaryDO> selectPage(Long deviceId,
                                                 Long startTsMillis, Long endTsMillis, int pageNo, int pageSize);

    /**
     * 按设备和班次查询单条记录
     */
    DeviceMetricSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    /**
     * 查询指定时间范围内已完成的班次记录
     *
     * @param deviceId      设备ID
     * @param startShiftDate 开始班次
     * @param endShiftDate   结束班次
     * @return 班次记录列表（按 shift_end_ts 升序）
     */
    List<DeviceMetricSummaryDO> selectFinalizedInRange(Long deviceId, LocalDate startShiftDate, LocalDate endShiftDate);

    /**
     * 查询在指定时间范围内有已完成汇总记录的设备ID列表
     * 用于工厂指标汇总任务，只处理有设备指标汇总数据的设备
     *
     * @param startTsMillis 开始时间戳（豪秒），查询 shift_end_ts >= startTs 的记录，可为null表示不限制
     * @param endTsMillis   结束时间戳（豪秒），查询 shift_end_ts <= endTs 的记录
     * @return 设备ID列表（去重）
     */
    List<Long> findDistinctDeviceIdsWithFinalizedSummaries(Long startTsMillis, Long endTsMillis);

    /**
     * 批量查询多个设备在指定时间范围内已完成的班次记录
     *
     * @param deviceIds 设备ID列表
     * @param startTsMillis   开始时间戳（豪秒，可选，为null则不限制）
     * @param endTsMillis     结束时间戳（豪秒，可选，为null则不限制）
     * @return 设备指标汇总Map，key为设备ID，value为该设备的班次记录列表（按 shift_end_ts 升序）
     */
    Map<Long, List<DeviceMetricSummaryDO>> selectFinalizedInRangeBatch(
            List<Long> deviceIds, Long startTsMillis, Long endTsMillis);

    /**
     * 查询指定工厂、班次日期、班次编码的所有设备指标数据（已完成的记录）
     * 用于统计TopN设备
     *
     * @param orgFactoryId 工厂ID
     * @param shiftDate    班次日期
     * @param shiftCode    班次编码（可选，为null则查询当天所有班次）
     * @return 设备指标汇总列表
     */
    List<DeviceMetricSummaryDO> selectByFactoryAndShift(Long orgFactoryId, LocalDate shiftDate, Integer shiftCode);

    void insert(DeviceMetricSummaryDO entity);

    void update(DeviceMetricSummaryDO entity);
}