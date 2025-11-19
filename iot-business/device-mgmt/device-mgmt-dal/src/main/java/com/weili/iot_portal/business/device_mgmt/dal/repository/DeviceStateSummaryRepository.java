package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceStateSummaryDO;

import java.util.List;

/**
 * 设备状态汇总仓储
 */
public interface DeviceStateSummaryRepository {

    List<DeviceStateSummaryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs);
}


