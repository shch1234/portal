package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolUsageWebhookRequest;

public interface ToolUsageWebhookService {

    void handleToolUsageWebhook(ToolUsageWebhookRequest request, String secret);
}

