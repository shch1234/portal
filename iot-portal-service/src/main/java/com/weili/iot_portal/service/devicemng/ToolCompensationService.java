package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.ToolCompensationVO;

public interface ToolCompensationService {

    ToolCompensationVO getCurrent(String factoryId, String deviceId);
}

