package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.ToolCompensationVO;

public interface ToolCompensationService {

    ToolCompensationVO getCurrent(String tenantId, String factoryId, String deviceId);
}

