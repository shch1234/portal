package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProgramInfoWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    private Long ts;

    private String programName;

    private String programPath;

    private Integer currentLine;

    private String currentCode;
}

