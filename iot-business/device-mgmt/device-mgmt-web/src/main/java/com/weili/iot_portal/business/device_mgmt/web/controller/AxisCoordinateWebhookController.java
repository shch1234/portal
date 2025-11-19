package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.AxisCoordinateWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.AxisCoordinateWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 轴坐标Webhook接收接口
 */
@Slf4j
@Tag(name = "设备管理-Webhook")
@RestController
@RequestMapping("/api/device-mgmt/v1/webhook/axis-coordinates")
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

