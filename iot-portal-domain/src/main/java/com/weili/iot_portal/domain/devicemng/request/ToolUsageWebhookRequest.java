package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 刀具使用记录 Webhook 请求
 */
@Data
public class ToolUsageWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    @Valid
    @NotEmpty
    private List<ToolUsageItem> items;

    @Data
    public static class ToolUsageItem {
        @NotBlank
        private String toolNumber;
        private String toolHolderNumber;
        @NotNull
        private Long startTs;
        @NotNull
        private Long endTs;
        private Long durationMs;
    }
}

