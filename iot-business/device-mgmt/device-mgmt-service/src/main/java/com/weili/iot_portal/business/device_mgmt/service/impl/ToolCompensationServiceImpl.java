package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationVO;
import com.weili.iot_portal.business.device_mgmt.service.ToolCompensationService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import com.weili.iot_portal.business.device_mgmt.service.support.ToolCompensationCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolCompensationServiceImpl implements ToolCompensationService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ToolCompensationCache toolCompensationCache;

    @Override
    public ToolCompensationVO getCurrent(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        return toolCompensationCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到刀具补偿数据，请稍后重试"));
    }
}

