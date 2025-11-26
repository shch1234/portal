package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.ToolCompensationWebhookRequest;
import com.weili.iot_portal.service.devicemng.ToolCompensationWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "刀具补偿Webhook")
@RestController
@RequestMapping("/webhook/tool-compensation")
@RequiredArgsConstructor
public class ToolCompensationWebhookController {

    private final ToolCompensationWebhookService service;

    @PostMapping
    @Operation(summary = "接收TB推送的刀具补偿Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody ToolCompensationWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        service.handleWebhook(request, secret);
        return CommonResult.success(null);
    }
}

