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

    /**
     * 插入新记录（自动处理唯一约束冲突）
     * <p>
     * 在插入前会先删除已存在的相同唯一约束的记录，避免冲突
     * </p>
     *
     * @param record 要插入的记录
     */
    void insert(DeviceToolCompensationDO record);

    void updateById(DeviceToolCompensationDO record);

    /**
     * 关闭活跃记录（仅更新 active 和 end_ts 字段）
     * <p>
     * 使用 LambdaUpdateWrapper 仅更新需要的字段，避免更新其他字段导致唯一约束冲突
     * </p>
     *
     * @param id 记录ID
     * @param endTs 结束时间戳
     * @param active 是否活跃（通常为0表示关闭）
     */
    void deactivateById(Long id, Long endTs, Integer active);
}


