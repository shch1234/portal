package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.ProgramCodeWebhookRequest;

public interface ProgramCodeWebhookService {

    void handleProgramCodeWebhook(ProgramCodeWebhookRequest request, String secret);
}

