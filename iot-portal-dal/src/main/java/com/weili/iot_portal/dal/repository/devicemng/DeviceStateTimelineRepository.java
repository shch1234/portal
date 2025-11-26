package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;

import java.util.List;

/**
 * 设备状态时间线仓储
 */
public interface DeviceStateTimelineRepository {

    List<DeviceStateTimelineDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs);

    List<DeviceStateTimelineDO> selectRecent(String tenantId, String deviceId, Long startTs, int limit);
}


