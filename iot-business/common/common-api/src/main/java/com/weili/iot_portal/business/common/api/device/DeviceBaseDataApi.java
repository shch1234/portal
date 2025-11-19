package com.weili.iot_portal.business.common.api.device;

import com.weili.iot_portal.business.common.domain.model.device.DeviceBaseInfoVO;

import java.util.List;

/**
 * 设备基础数据查询API接口（公共接口）
 * 
 * <p>用于各业务模块查询设备基础信息，实现类由 device-base 模块提供
 * 
 * <p>设计原则：
 * - 通过接口解耦，各业务模块只依赖接口，不依赖具体实现
 * - 实现类放在 device-base-service 模块中
 * - 支持工厂数据隔离
 * - 支持缓存，提高性能
 */
public interface DeviceBaseDataApi {

    /**
     * 根据设备ID查询设备基础信息
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 设备基础信息
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不存在或不属于该工厂
     */
    DeviceBaseInfoVO getDeviceById(String tenantId, String factoryId, String deviceId);

    /**
     * 根据设备编号查询设备基础信息
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceCode 设备编号（威力编号）
     * @return 设备基础信息
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不存在或不属于该工厂
     */
    DeviceBaseInfoVO getDeviceByCode(String tenantId, String factoryId, String deviceCode);

    /**
     * 批量查询设备基础信息
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceIds 设备ID列表
     * @return 设备基础信息列表
     */
    List<DeviceBaseInfoVO> getDevicesByIds(String tenantId, String factoryId, List<String> deviceIds);

    /**
     * 根据工厂/车间查询设备列表
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（必填）
     * @param workshopId 车间ID（可选，如果为null则查询工厂下所有设备）
     * @return 设备基础信息列表
     */
    List<DeviceBaseInfoVO> getDevicesByFactory(String tenantId, String factoryId, String workshopId);

    /**
     * 根据ThingsBoard设备ID查询设备基础信息
     * 
     * @param tenantId 租户ID
     * @param tbDeviceId ThingsBoard设备ID
     * @return 设备基础信息
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不存在
     */
    DeviceBaseInfoVO getDeviceByTbDeviceId(String tenantId, String tbDeviceId);
}

