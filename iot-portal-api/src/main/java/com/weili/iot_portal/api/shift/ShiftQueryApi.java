package com.weili.iot_portal.api.shift;

import com.weili.iot_portal.domain.shift.ShiftInfoVO;
import com.weili.iot_portal.domain.shift.ShiftTimeRangeVO;

/**
 * 班次查询API接口（公共接口）
 * 
 * <p>用于各业务模块查询班次信息，实现类由 device-mgmt 模块提供
 * 
 * <p>设计原则：
 * - 通过接口解耦，各业务模块只依赖接口，不依赖具体实现
 * - 实现类可以放在 device-mgmt 模块中（因为班次配置数据在该模块）
 * - 未来可以通过 Spring Bean 或 RPC 方式提供实现
 */
public interface ShiftQueryApi {

    /**
     * 获取指定设备在当前时间的班次信息
     * 
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒），如果为null则使用当前时间
     * @return 班次信息
     * @throws com.weili.basic.common.exception.ServiceException 如果设备未配置班次信息
     */
    ShiftInfoVO getDeviceCurrentShift(String factoryId, String deviceId, Long timestamp);

    /**
     * 获取工厂/车间下的代表性设备的当前班次信息
     * 
     * <p>用于查询工厂/车间级别的当前班次，会从该工厂/车间下的设备中找到第一个有班次配置的设备
     * 
     * @param factoryId 工厂ID（必填）
     * @param workshopId 车间ID（可选，如果不填则从工厂下所有设备中查找）
     * @param timestamp 时间戳（毫秒），如果为null则使用当前时间
     * @return 班次信息
     * @throws com.weili.basic.common.exception.ServiceException 如果工厂/车间下没有设备配置班次信息
     */
    ShiftInfoVO getFactoryCurrentShift(String factoryId, String workshopId, Long timestamp);

    /**
     * 计算班次的时间范围（带工厂验证）
     * 
     * <p>根据设备在当前时间的班次配置，计算班次的开始和结束时间戳
     * 支持跨天班次的正确处理
     * 
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒），如果为null则使用当前时间
     * @return 班次时间范围
     * @throws com.weili.basic.common.exception.ServiceException 如果设备未配置班次信息
     */
    ShiftTimeRangeVO calculateShiftRange(String factoryId, String deviceId, Long timestamp);
}

