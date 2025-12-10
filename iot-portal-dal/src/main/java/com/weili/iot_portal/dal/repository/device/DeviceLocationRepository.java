package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;

import java.util.List;
import java.util.Optional;

public interface DeviceLocationRepository {

    Optional<DeviceLocationDO> findByDeviceId(String deviceId);

    List<DeviceLocationDO> findByDeviceIds(List<String> deviceIds);

    List<String> findDeviceIdsByLocationCode(String locationCode);

    void insert(DeviceLocationDO entity);

    void update(DeviceLocationDO entity);
}

