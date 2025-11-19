package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.CurrentToolInfoVO;

public interface ToolInfoService {

    CurrentToolInfoVO getCurrentToolInfo(String tenantId, String factoryId, String deviceId);
}

