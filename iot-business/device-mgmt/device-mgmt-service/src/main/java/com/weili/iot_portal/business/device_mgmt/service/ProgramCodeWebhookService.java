package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProgramCodeWebhookRequest;

public interface ProgramCodeWebhookService {

    void handleProgramCodeWebhook(ProgramCodeWebhookRequest request, String secret);
}

