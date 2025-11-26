package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.ToolUsageWebhookRequest;

public interface ToolUsageWebhookService {

    void handleToolUsageWebhook(ToolUsageWebhookRequest request, String secret);
}

