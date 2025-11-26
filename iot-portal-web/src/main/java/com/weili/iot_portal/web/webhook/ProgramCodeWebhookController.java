package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.ProgramCodeWebhookRequest;
import com.weili.iot_portal.service.devicemng.ProgramCodeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "程序代码Webhook")
@RestController
@RequestMapping("/webhook/program-code")
@RequiredArgsConstructor
public class ProgramCodeWebhookController {

    private final ProgramCodeWebhookService webhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的程序代码Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody ProgramCodeWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        webhookService.handleProgramCodeWebhook(request, secret);
        return CommonResult.success(null);
    }
}

