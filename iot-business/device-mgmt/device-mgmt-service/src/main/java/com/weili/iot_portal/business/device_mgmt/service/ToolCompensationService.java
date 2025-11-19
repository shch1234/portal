package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationVO;

public interface ToolCompensationService {

    ToolCompensationVO getCurrent(String tenantId, String factoryId, String deviceId);
}

