package com.weili.iot_portal.service.shift;

import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.service.shift.model.ShiftInfo;
import com.weili.iot_portal.service.shift.model.ShiftTimeRange;

import java.util.List;

/**
 * 班次配置服务接口
 * 定义班次配置相关的业务操作
 */
public interface IShiftConfigService {

    /**
     * 获取设备在当前时间的生效班次配置（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次配置
     */
    DeviceShiftConfigDO getCurrentConfiguration(String factoryId, String deviceId, long timestamp);

    /**
     * 根据时间点确定当前班次信息（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息
     */
    ShiftInfo getCurrentShift(String factoryId, String deviceId, long timestamp);

    /**
     * 计算班次的时间范围（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次时间范围
     */
    ShiftTimeRange calculateShiftRange(String factoryId, String deviceId, long timestamp);

    /**
     * 获取时间范围内的所有配置版本（带工厂验证）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param startTs 开始时间戳
     * @param endTs 结束时间戳
     * @return 班次配置列表（按生效时间倒序）
     */
    List<DeviceShiftConfigDO> getConfigurationsInRange(String factoryId, String deviceId, long startTs, long endTs);
}
