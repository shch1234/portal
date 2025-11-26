package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.request.FeedRateWebhookRequest;

public interface FeedRateWebhookService {

    void handleFeedRateWebhook(FeedRateWebhookRequest request, String secret);
}

