package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.SpindleSpeedWebhookRequest;
import com.weili.iot_portal.service.devicemng.SpindleSpeedWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "主轴转速Webhook")
@RestController
@RequestMapping("/webhook/spindle-speed")
@RequiredArgsConstructor
public class SpindleSpeedWebhookController {

    private final SpindleSpeedWebhookService spindleSpeedWebhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的主轴转速Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody SpindleSpeedWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        spindleSpeedWebhookService.handleSpindleSpeedWebhook(request, secret);
        return CommonResult.success(null);
    }
}

