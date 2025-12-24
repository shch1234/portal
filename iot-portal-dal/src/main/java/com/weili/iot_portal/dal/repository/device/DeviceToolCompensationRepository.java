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

    /**
     * 分页查询设备的有效刀补记录
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param offset 偏移量
     * @param limit 限制条数
     * @return 刀补记录列表
     */
    List<DeviceToolCompensationDO> findActiveByDeviceWithPage(Long factoryId, String deviceId, Integer offset, Integer limit);

    /**
     * 统计设备的有效刀补记录总数
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 记录总数
     */
    Long countActiveByDevice(Long factoryId, String deviceId);

    void insert(DeviceToolCompensationDO record);

    void updateById(DeviceToolCompensationDO record);
}


