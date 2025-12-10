package com.weili.iot_portal.dal.repository.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;

import java.util.Optional;

public interface DeviceNetworkConfigRepository {

    Optional<DeviceNetworkConfigDO> findById(String id);

    Optional<DeviceNetworkConfigDO> findByDeviceInfoId(String deviceInfoId);

    boolean existsByDeviceInfoId(String deviceInfoId, String excludeId);

    PageResult<DeviceNetworkConfigDO> selectPage(DeviceNetworkConfigPageQuery query);

    void insert(DeviceNetworkConfigDO entity);

    void update(DeviceNetworkConfigDO entity);

    boolean deleteById(String id);
}

