package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.SpindleSpeedWebhookRequest;

public interface SpindleSpeedWebhookService {

    void handleSpindleSpeedWebhook(SpindleSpeedWebhookRequest request, String secret);
}

