package com.weili.iot_portal.api.device;

import com.weili.iot_portal.domain.devicemng.DeviceStatusVO;

import java.util.List;
import java.util.Map;

/**
 * 设备状态服务接口（供其他模块调用）
 * 
 * <p>提供设备状态相关的查询服务，其他模块可以通过此接口获取设备状态信息。
 * 
 * <p><b>注意：所有方法都需要提供factoryId参数，以确保工厂数据隔离。</b>
 * 如果设备不属于指定的工厂，将抛出ServiceException。
 */
public interface DeviceStateApi {

    /**
     * 获取设备当前状态
     * 
     * <p><b>工厂隔离：</b>会验证设备是否属于指定的工厂，如果设备不属于该工厂，将抛出异常。
     *
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 设备状态信息，如果设备不存在或不属于指定工厂则返回null或抛出异常
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不属于指定工厂
     */
    DeviceStatusVO getCurrentStatus(String factoryId, String deviceId);

    /**
     * 批量获取设备状态
     * 
     * <p><b>工厂隔离：</b>会验证所有设备是否属于指定的工厂，只返回属于该工厂的设备状态。
     *
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceIds 设备ID列表
     * @return 属于指定工厂的设备状态Map，key为deviceId，value为DeviceStatusVO
     */
    Map<String, DeviceStatusVO> batchGetStatus(String factoryId, List<String> deviceIds);

    /**
     * 检查设备是否有报警
     * 
     * <p><b>工厂隔离：</b>会验证设备是否属于指定的工厂。
     *
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 是否有报警（如果设备不属于指定工厂，返回false或抛出异常）
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不属于指定工厂
     */
    boolean hasAlarm(String factoryId, String deviceId);
}

