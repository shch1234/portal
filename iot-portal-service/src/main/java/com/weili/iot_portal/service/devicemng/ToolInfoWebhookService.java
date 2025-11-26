package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.CurrentToolWebhookRequest;

public interface ToolInfoWebhookService {

    void handleToolInfoWebhook(CurrentToolWebhookRequest request, String secret);
}

