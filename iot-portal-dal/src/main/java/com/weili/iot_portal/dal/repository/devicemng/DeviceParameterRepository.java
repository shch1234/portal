package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceParameterDO;

import java.util.List;

/**
 * 设备参数仓储
 */
public interface DeviceParameterRepository {

    List<DeviceParameterDO> selectCurrent(String deviceId);

    List<DeviceParameterDO> selectHistory(String deviceId, Long startTs, Long endTs);

    void expireCurrent(String deviceId, String parameterType, long endTs);

    void insert(DeviceParameterDO entity);
}


