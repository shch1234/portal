package com.weili.iot_portal.dal.repository.devicebase;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceLocationDO;

import java.util.List;
import java.util.Optional;

public interface DeviceLocationRepository {

    Optional<DeviceLocationDO> findByDeviceId(String tenantId, String deviceId);

    List<DeviceLocationDO> findByDeviceIds(String tenantId, List<String> deviceIds);

    List<String> findDeviceIdsByLocationCode(String tenantId, String locationCode);

    void insert(DeviceLocationDO entity);

    void update(DeviceLocationDO entity);
}

