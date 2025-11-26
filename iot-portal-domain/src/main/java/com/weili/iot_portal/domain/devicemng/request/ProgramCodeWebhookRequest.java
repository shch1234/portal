package com.weili.iot_portal.domain.devicemng.request;

import com.weili.iot_portal.common.enums.ProgramCodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProgramCodeWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    @NotNull
    private ProgramCodeType type;

    private String content;

    private Long ts;
}

