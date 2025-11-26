package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.FeedRateWebhookRequest;
import com.weili.iot_portal.service.devicemng.FeedRateWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "进给率Webhook")
@RestController
@RequestMapping("/webhook/feed-rate")
@RequiredArgsConstructor
public class FeedRateWebhookController {

    private final FeedRateWebhookService feedRateWebhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的进给率Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody FeedRateWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        feedRateWebhookService.handleFeedRateWebhook(request, secret);
        return CommonResult.success(null);
    }
}

