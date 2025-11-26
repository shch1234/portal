package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.ProgramInfoWebhookRequest;

public interface ProgramInfoWebhookService {

    void handleProgramInfoWebhook(ProgramInfoWebhookRequest request, String secret);
}

