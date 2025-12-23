package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;

import java.util.List;

/**
 * 设备参数仓储
 */
public interface DeviceParamConfigRepository {

    List<DeviceParamConfigDO> selectCurrent(Long deviceId);

    List<DeviceParamConfigDO> selectHistory(Long deviceId, Long startTs, Long endTs);

    void expireCurrent(Long deviceId, String parameterType, long endTs);

    void insert(DeviceParamConfigDO entity);
}


