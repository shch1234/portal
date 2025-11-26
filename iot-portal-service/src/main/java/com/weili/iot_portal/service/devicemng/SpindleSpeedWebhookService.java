package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.SpindleSpeedWebhookRequest;

public interface SpindleSpeedWebhookService {

    void handleSpindleSpeedWebhook(SpindleSpeedWebhookRequest request, String secret);
}

