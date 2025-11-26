package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.ProgramInfoWebhookRequest;
import com.weili.iot_portal.service.devicemng.ProgramInfoWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "程序信息Webhook")
@RestController
@RequestMapping("/webhook/program-info")
@RequiredArgsConstructor
public class ProgramInfoWebhookController {

    private final ProgramInfoWebhookService webhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的程序信息Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody ProgramInfoWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        webhookService.handleProgramInfoWebhook(request, secret);
        return CommonResult.success(null);
    }
}

