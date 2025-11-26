package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.domain.devicemng.AxisCoordinateListVO;
import com.weili.iot_portal.service.devicemng.AxisCoordinateService;
import com.weili.iot_portal.service.support.AxisCoordinateCache;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 轴坐标服务实现：从缓存读取TB推送的最新数据
 */
@Service
@RequiredArgsConstructor
public class AxisCoordinateServiceImpl implements AxisCoordinateService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final AxisCoordinateCache axisCoordinateCache;

    @Override
    public AxisCoordinateListVO getCurrentAxisCoordinates(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);

        return axisCoordinateCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到设备的轴坐标数据，请稍后重试"));
    }
}

