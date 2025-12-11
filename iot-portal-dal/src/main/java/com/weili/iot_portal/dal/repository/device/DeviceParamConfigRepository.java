package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;

import java.util.List;

/**
 * 设备参数仓储
 */
public interface DeviceParamConfigRepository {

    List<DeviceParamConfigDO> selectCurrent(String deviceId);

    List<DeviceParamConfigDO> selectHistory(String deviceId, Long startTs, Long endTs);

    void expireCurrent(String deviceId, String parameterType, long endTs);

    void insert(DeviceParamConfigDO entity);
}


