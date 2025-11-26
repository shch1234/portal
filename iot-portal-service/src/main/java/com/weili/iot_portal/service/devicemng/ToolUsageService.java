package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.ToolUsageHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.ToolUsageHistoryReq;

public interface ToolUsageService {

    ToolUsageHistoryVO getHistory(String tenantId, String factoryId, ToolUsageHistoryReq request);
}

