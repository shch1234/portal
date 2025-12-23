package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;

import java.util.Optional;

public interface DeviceNetworkConfigRepository {

    Optional<DeviceNetworkConfigDO> findById(Long id);

    Optional<DeviceNetworkConfigDO> findByDeviceInfoId(Long deviceInfoId);

    boolean existsByDeviceInfoId(String deviceInfoId, String excludeId);

    PageResult<DeviceNetworkConfigDO> selectPage(DeviceNetworkConfigPageQuery query);

    void insert(DeviceNetworkConfigDO entity);

    void update(DeviceNetworkConfigDO entity);

    boolean deleteById(Long id);
}

