package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.request.FeedRateWebhookRequest;

public interface FeedRateWebhookService {

    void handleFeedRateWebhook(FeedRateWebhookRequest request, String secret);
}

