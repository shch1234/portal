package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;
import com.weili.iot_portal.service.devicemng.ToolInfoService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import com.weili.iot_portal.service.support.ToolInfoCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolInfoServiceImpl implements ToolInfoService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ToolInfoCache toolInfoCache;

    @Override
    public CurrentToolInfoVO getCurrentToolInfo(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        return toolInfoCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到刀具信息，请稍后重试"));
    }
}

