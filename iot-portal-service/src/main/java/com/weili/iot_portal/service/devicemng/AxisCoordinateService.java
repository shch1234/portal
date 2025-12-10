package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.AxisCoordinateListVO;

/**
 * 轴坐标服务
 */
public interface AxisCoordinateService {

    /**
     * 获取设备当前轴坐标列表
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 轴坐标列表
     */
    AxisCoordinateListVO getCurrentAxisCoordinates(String factoryId, String deviceId);
}

