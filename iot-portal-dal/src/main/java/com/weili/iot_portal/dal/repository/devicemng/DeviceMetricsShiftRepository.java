package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceMetricsShiftDO;

import java.util.Optional;

/**
 * 设备班次指标仓储
 */
public interface DeviceMetricsShiftRepository {

    Optional<DeviceMetricsShiftDO> selectLatestFinalized(String tenantId, String deviceId);

    PageResult<DeviceMetricsShiftDO> selectPage(String tenantId, String deviceId,
                                                Long startTs, Long endTs, int pageNo, int pageSize);
}


