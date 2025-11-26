package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.ToolCompensationWebhookRequest;

public interface ToolCompensationWebhookService {

    void handleWebhook(ToolCompensationWebhookRequest request, String secret);
}

