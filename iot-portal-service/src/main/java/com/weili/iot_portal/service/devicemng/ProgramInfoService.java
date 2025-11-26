package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.common.enums.ProgramCodeType;
import com.weili.iot_portal.domain.devicemng.ProgramCodeVO;
import com.weili.iot_portal.domain.devicemng.ProgramInfoVO;

public interface ProgramInfoService {

    ProgramInfoVO getCurrentProgramInfo(String tenantId, String factoryId, String deviceId);

    ProgramCodeVO getProgramCode(String tenantId, String factoryId, String deviceId, ProgramCodeType type);
}

