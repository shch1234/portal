package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 当前刀具信息 Webhook 请求
 */
@Data
public class CurrentToolWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    @NotNull
    private Long ts;

    private String toolNumber;

    private String toolHolderNumber;

    private Double lengthComp;

    private Double radiusComp;

    private Double lengthWear;

    private Double radiusWear;
}

