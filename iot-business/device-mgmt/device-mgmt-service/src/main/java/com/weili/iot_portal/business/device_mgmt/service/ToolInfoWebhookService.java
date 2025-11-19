package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.CurrentToolWebhookRequest;

public interface ToolInfoWebhookService {

    void handleToolInfoWebhook(CurrentToolWebhookRequest request, String secret);
}

