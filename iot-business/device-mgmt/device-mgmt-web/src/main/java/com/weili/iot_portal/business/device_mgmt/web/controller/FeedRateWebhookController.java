package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.FeedRateWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.FeedRateWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备管理-Webhook")
@RestController
@RequestMapping("/api/device-mgmt/v1/webhook/feed-rate")
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

