package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;

import java.util.List;

/**
 * 设备状态汇总仓储
 */
public interface DeviceStateSummaryRepository {

    List<DeviceStateSummaryDO> selectByRange(String deviceId, Long startTs, Long endTs);
}


