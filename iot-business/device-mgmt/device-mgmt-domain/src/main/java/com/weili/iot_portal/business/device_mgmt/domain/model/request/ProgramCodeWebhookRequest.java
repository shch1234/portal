package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import com.weili.iot_portal.business.device_mgmt.domain.enums.ProgramCodeType;
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

