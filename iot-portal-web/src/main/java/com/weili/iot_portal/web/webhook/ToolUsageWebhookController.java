package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.ToolUsageWebhookRequest;
import com.weili.iot_portal.service.devicemng.ToolUsageWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "刀具使用记录Webhook")
@RestController
@RequestMapping("/webhook/tool-usage")
@RequiredArgsConstructor
public class ToolUsageWebhookController {

    private final ToolUsageWebhookService webhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的刀具使用记录Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody ToolUsageWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        webhookService.handleToolUsageWebhook(request, secret);
        return CommonResult.success(null);
    }
}

