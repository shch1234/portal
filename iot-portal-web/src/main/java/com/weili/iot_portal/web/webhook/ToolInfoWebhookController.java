package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.CurrentToolWebhookRequest;
import com.weili.iot_portal.service.devicemng.ToolInfoWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "刀具信息Webhook")
@RestController
@RequestMapping("/webhook/tool-info")
@RequiredArgsConstructor
public class ToolInfoWebhookController {

    private final ToolInfoWebhookService toolInfoWebhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的当前刀具信息Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody CurrentToolWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        toolInfoWebhookService.handleToolInfoWebhook(request, secret);
        return CommonResult.success(null);
    }
}

