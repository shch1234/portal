package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * ThingsBoard 轴坐标Webhook请求
 */
@Data
public class AxisCoordinateWebhookRequest {

    /**
     * 幂等ID（TB消息ID），用于防重复处理
     */
    @NotBlank(message = "messageId不能为空")
    private String messageId;

    /**
     * 租户ID
     */
    @NotBlank(message = "tenantId不能为空")
    private String tenantId;

    /**
     * 设备编号（威力编号）
     */
    @NotBlank(message = "deviceCode不能为空")
    private String deviceCode;

    /**
     * TB设备ID（可选，用于辅助排查）
     */
    private String tbDeviceId;

    /**
     * 数据产生时间戳（毫秒）
     */
    @NotNull(message = "ts不能为空")
    private Long ts;

    /**
     * 轴坐标数据
     */
    @NotEmpty(message = "axes不能为空")
    @Valid
    private List<AxisCoordinatePayload> axes;

    @Data
    public static class AxisCoordinatePayload {
        @NotBlank(message = "axisName不能为空")
        private String axisName;

        private Double absoluteCoordinate;
        private Double relativeCoordinate;
        private Double machineCoordinate;
        private Double remainingCoordinate;
    }
}

