package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备状态汇总仓储
 */
public interface DeviceStateSummaryRepository {

    /**
     * 按时间范围查询设备状态汇总
     *
     * @param deviceId 设备ID，可为null表示查询所有设备
     * @param startTs 开始时间戳（毫秒），查询 shift_end_ts >= startTs 的记录，可为null表示不限制
     * @param endTs 结束时间戳（毫秒），查询 shift_start_ts <= endTs 的记录，可为null表示不限制
     * @return 状态汇总记录列表
     */
    List<DeviceStateSummaryDO> selectByRange(Long deviceId, Long startTs, Long endTs);

    /**
     * 查询指定日期范围内未完成汇总的记录
     */
    List<DeviceStateSummaryDO> selectPending(LocalDate startDate, LocalDate endDate);

    /**
     * 根据设备与班次唯一键查询
     */
    DeviceStateSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    /**
     * 新增汇总
     */
    void insert(DeviceStateSummaryDO entity);

    /**
     * 更新汇总
     */
    void update(DeviceStateSummaryDO entity);

    /**
     * 查询在指定时间范围内有已完成汇总记录的设备ID列表
     * 用于指标汇总任务，只处理有状态汇总数据的设备
     *
     * @param startTs 开始时间戳（毫秒），查询 shift_end_ts >= startTs 的记录，可为null表示不限制
     * @param endTs 结束时间戳（毫秒），查询 shift_end_ts <= endTs 的记录
     * @return 设备ID列表（去重）
     */
    List<Long> findDistinctDeviceIdsWithFinalizedSummaries(Long startTs, Long endTs);
}


