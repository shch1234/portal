package com.weili.iot_portal.web.webhook;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.devicemng.request.AxisCoordinateWebhookRequest;
import com.weili.iot_portal.service.devicemng.AxisCoordinateWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 轴坐标Webhook接收接口
 */
@Tag(name = "轴坐标Webhook")
@RestController
@RequestMapping("/webhook/axis-coordinates")
@RequiredArgsConstructor
public class AxisCoordinateWebhookController {

    private final AxisCoordinateWebhookService axisCoordinateWebhookService;

    @PostMapping
    @Operation(summary = "接收TB推送的轴坐标Webhook")
    public CommonResult<Void> receive(@Valid @RequestBody AxisCoordinateWebhookRequest request,
                                      @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {
        axisCoordinateWebhookService.handleAxisCoordinateWebhook(request, secret);
        return CommonResult.success(null);
    }
}

