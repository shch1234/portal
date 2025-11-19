package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceParameterDO;

import java.util.List;

/**
 * 设备参数仓储
 */
public interface DeviceParameterRepository {

    List<DeviceParameterDO> selectCurrent(String tenantId, String deviceId);

    List<DeviceParameterDO> selectHistory(String tenantId, String deviceId, Long startTs, Long endTs);

    void expireCurrent(String tenantId, String deviceId, String parameterType, long endTs);

    void insert(DeviceParameterDO entity);
}


