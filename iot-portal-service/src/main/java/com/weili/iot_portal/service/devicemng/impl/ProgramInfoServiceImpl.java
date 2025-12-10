package com.weili.iot_portal.service.devicemng.impl;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.common.enums.ProgramCodeType;
import com.weili.iot_portal.domain.devicemng.ProgramCodeVO;
import com.weili.iot_portal.domain.devicemng.ProgramInfoVO;
import com.weili.iot_portal.service.devicemng.ProgramInfoService;
import com.weili.iot_portal.service.support.DeviceFactoryValidator;
import com.weili.iot_portal.service.support.ProgramCodeCache;
import com.weili.iot_portal.service.support.ProgramInfoCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProgramInfoServiceImpl implements ProgramInfoService {

    private final DeviceFactoryValidator deviceFactoryValidator;
    private final ProgramInfoCache programInfoCache;
    private final ProgramCodeCache programCodeCache;

    @Override
    public ProgramInfoVO getCurrentProgramInfo(String factoryId, String deviceId) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        return programInfoCache.get(deviceId)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到程序信息，请稍后重试"));
    }

    @Override
    public ProgramCodeVO getProgramCode(String factoryId, String deviceId, ProgramCodeType type) {
        deviceFactoryValidator.ensureDeviceBelongsToFactory(factoryId, deviceId);
        return programCodeCache.get(deviceId, type)
                .orElseThrow(() -> new ServiceException(
                        ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "暂未收到" + (type == ProgramCodeType.M_CODE ? "M" : "G") + "代码，请稍后重试"));
    }
}

