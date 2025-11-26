package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.iot_portal.dal.dataobject.devicemng.ShiftConfigurationDO;

import java.util.List;
import java.util.Optional;

/**
 * 班次配置仓储
 */
public interface ShiftConfigurationRepository {

    /**
     * 查询设备在当前时间生效的班次配置
     *
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳
     * @return 班次配置
     */
    Optional<ShiftConfigurationDO> findActiveByDeviceAndTime(String tenantId, String deviceId, long timestamp);

    /**
     * 查询设备在时间范围内的所有班次配置（按生效时间倒序）
     *
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param startTs 开始时间戳
     * @param endTs 结束时间戳
     * @return 班次配置列表
     */
    List<ShiftConfigurationDO> findByDeviceAndTimeRange(String tenantId, String deviceId, long startTs, long endTs);

    /**
     * 插入班次配置
     *
     * @param entity 班次配置
     */
    void insert(ShiftConfigurationDO entity);

    /**
     * 更新班次配置的生效结束时间（用于配置变更）
     *
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param endTs 生效结束时间
     */
    void updateEffectiveEndTs(String tenantId, String deviceId, long endTs);
}

