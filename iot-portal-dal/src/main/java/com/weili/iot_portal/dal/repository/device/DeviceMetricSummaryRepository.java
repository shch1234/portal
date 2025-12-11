package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 设备班次指标仓储
 */
public interface DeviceMetricSummaryRepository {

    Optional<DeviceMetricSummaryDO> selectLatestFinalized(String deviceId);

    PageResult<DeviceMetricSummaryDO> selectPage(String deviceId,
                                                Long startTs, Long endTs, int pageNo, int pageSize);

    /**
     * 按设备和班次查询单条记录
     */
    DeviceMetricSummaryDO findByShift(String deviceId, LocalDate shiftDate, String shiftCode);

    void insert(DeviceMetricSummaryDO entity);

    void update(DeviceMetricSummaryDO entity);
}