package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;

public interface ToolInfoService {

    CurrentToolInfoVO getCurrentToolInfo(String factoryId, String deviceId);
}

