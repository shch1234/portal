package com.weili.iot_portal.service.shift;

import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;

import java.util.List;

/**
 * 班次配置服务接口
 * 负责班次配置的查询和管理，不涉及班次计算逻辑
 */
public interface IShiftConfigService {

    /**
     * 获取设备在当前时间的生效班次配置（带工厂验证）
     * 如果设备未配置班次，返回默认班次配置
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次配置
     */
    DeviceShiftConfigDO getCurrentConfiguration(Long factoryId, Long deviceId, long timestamp);

    /**
     * 获取时间范围内的所有配置版本（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param startTs 开始时间戳
     * @param endTs 结束时间戳
     * @return 班次配置列表（按生效时间倒序）
     */
    List<DeviceShiftConfigDO> getConfigurationsInRange(Long factoryId, Long deviceId, long startTs, long endTs);
}
