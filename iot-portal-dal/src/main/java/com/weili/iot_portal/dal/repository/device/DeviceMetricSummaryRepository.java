package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 设备班次指标仓储
 */
public interface DeviceMetricSummaryRepository {

    Optional<DeviceMetricSummaryDO> selectLatestFinalized(Long deviceId);

    PageResult<DeviceMetricSummaryDO> selectPage(Long deviceId,
                                                Long startTs, Long endTs, int pageNo, int pageSize);

    /**
     * 按设备和班次查询单条记录
     */
    DeviceMetricSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    /**
     * 查询统计时间点前已完成的班次记录
     *
     * @param deviceId 设备ID
     * @param endTs    统计时间戳（秒）
     * @return 班次记录列表（按 shift_end_ts 升序）
     */
    java.util.List<DeviceMetricSummaryDO> selectFinalizedUpTo(Long deviceId, Long endTs);

    void insert(DeviceMetricSummaryDO entity);

    void update(DeviceMetricSummaryDO entity);
}