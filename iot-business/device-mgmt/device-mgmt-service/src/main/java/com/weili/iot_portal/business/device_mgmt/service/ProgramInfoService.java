package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.enums.ProgramCodeType;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramCodeVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProgramInfoVO;

public interface ProgramInfoService {

    ProgramInfoVO getCurrentProgramInfo(String tenantId, String factoryId, String deviceId);

    ProgramCodeVO getProgramCode(String tenantId, String factoryId, String deviceId, ProgramCodeType type);
}

