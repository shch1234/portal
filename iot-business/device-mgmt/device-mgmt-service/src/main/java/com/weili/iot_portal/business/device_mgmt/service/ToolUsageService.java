package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.ToolUsageHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolUsageHistoryReq;

public interface ToolUsageService {

    ToolUsageHistoryVO getHistory(String tenantId, String factoryId, ToolUsageHistoryReq request);
}

