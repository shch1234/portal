package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;

import java.util.Optional;

/**
 * 设备班次指标仓储
 */
public interface DeviceMetricSummaryRepository {

    Optional<DeviceMetricSummaryDO> selectLatestFinalized(String deviceId);

    PageResult<DeviceMetricSummaryDO> selectPage(String deviceId,
                                                Long startTs, Long endTs, int pageNo, int pageSize);
}