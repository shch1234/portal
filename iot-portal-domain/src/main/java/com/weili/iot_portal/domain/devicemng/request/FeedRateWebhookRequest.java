package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 进给率曲线 Webhook 请求
 */
@Data
public class FeedRateWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String deviceCode;

    private String tbDeviceId;

    @NotNull
    private Long ts;

    @Valid
    @NotEmpty
    private List<FeedRatePoint> points;

    /**
     * 倍率（实时值，可选）
     */
    private Double overrideValue;

    @Data
    public static class FeedRatePoint {
        @NotNull
        private Long ts;
        @NotNull
        private Double feedRate;
    }
}

