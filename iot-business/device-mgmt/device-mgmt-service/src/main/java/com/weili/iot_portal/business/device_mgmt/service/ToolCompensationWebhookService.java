package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolCompensationWebhookRequest;

public interface ToolCompensationWebhookService {

    void handleWebhook(ToolCompensationWebhookRequest request, String secret);
}

