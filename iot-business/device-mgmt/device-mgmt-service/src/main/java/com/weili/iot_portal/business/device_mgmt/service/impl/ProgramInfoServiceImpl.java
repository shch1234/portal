package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.device_mgmt.domain.enums.ProgramCodeType;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramCodeVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramInfoVO;
import com.weili.iot_portal.business.device_mgmt.service.ProgramInfoService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import com.weili.iot_portal.business.device_mgmt.service.support.ProgramCodeCache;
import com.weili.iot_portal.business.device_mgmt.service.support.ProgramInfoCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProgramInfoServiceImpl implements ProgramInfoService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ProgramInfoCache programInfoCache;
    private final ProgramCodeCache programCodeCache;

    @Override
    public ProgramInfoVO getCurrentProgramInfo(String tenantId, String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        return programInfoCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到程序信息，请稍后重试"));
    }

    @Override
    public ProgramCodeVO getProgramCode(String tenantId, String factoryId, String deviceId, ProgramCodeType type) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        return programCodeCache.get(deviceId, type)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到" + (type == ProgramCodeType.M_CODE ? "M" : "G") + "代码，请稍后重试"));
    }
}

