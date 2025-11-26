package com.weili.iot_portal.domain.devicemng.request;

import com.weili.iot_portal.common.enums.ToolCompensationDimension;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ToolCompensationWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    @Valid
    @NotEmpty
    private List<ToolCompensationItem> items;

    @Data
    public static class ToolCompensationItem {

        private Long ts;

        @NotNull
        private ToolCompensationDimension dimension;

        @NotNull
        private Integer slot;

        private Double shapeValue;

        private Double wearValue;
    }
}

