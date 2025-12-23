package com.weili.iot_portal.dal.repository.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;

import java.util.List;

public interface DeviceToolCompensationRepository {

    /**
     * 查询当前有效的刀补记录
     */
    DeviceToolCompensationDO findActive(Long deviceId, String toolHolderNo);

    /**
     * 查询设备的所有有效刀补记录（可按工厂过滤）
     */
    List<DeviceToolCompensationDO> findActiveByDevice(Long factoryId, String deviceId);

    void insert(DeviceToolCompensationDO record);

    void updateById(DeviceToolCompensationDO record);
}


