package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 主轴转速 Webhook 请求
 */
@Data
public class SpindleSpeedWebhookRequest {

    @NotBlank
    private String messageId;

    @NotBlank
    private String tenantId;

    /**
     * 威力编号
     */
    @NotBlank
    private String deviceCode;

    /**
     * TB设备ID（可选）
     */
    private String tbDeviceId;

    /**
     * 数据产生时间（毫秒）
     */
    @NotNull
    private Long ts;

    /**
     * 转速点位列表
     */
    @Valid
    @NotEmpty
    private List<SpindleSpeedPoint> points;

    @Data
    public static class SpindleSpeedPoint {
        @NotNull
        private Long ts;
        @NotNull
        private Double speed;
    }
}

