package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolCompensationWebhookRequest;
import com.weili.iot_portal.business.device_mgmt.service.ToolCompensationWebhookService;
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
@RequestMapping("/api/device-mgmt/v1/webhook/tool-compensation")
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

