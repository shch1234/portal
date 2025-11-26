package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;

import java.util.List;

/**
 * 设备状态汇总仓储
 */
public interface DeviceStateSummaryRepository {

    List<DeviceStateSummaryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs);
}


