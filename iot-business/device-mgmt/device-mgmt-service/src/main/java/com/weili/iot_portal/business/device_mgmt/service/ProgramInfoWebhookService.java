package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProgramInfoWebhookRequest;

public interface ProgramInfoWebhookService {

    void handleProgramInfoWebhook(ProgramInfoWebhookRequest request, String secret);
}

