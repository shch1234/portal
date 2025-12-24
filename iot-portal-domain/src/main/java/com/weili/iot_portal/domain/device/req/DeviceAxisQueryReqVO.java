package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备轴标签信息查询 Request VO
 */
@Schema(description = "设备轴标签信息查询 Request VO")
@Data
public class DeviceAxisQueryReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    @Schema(description = "曲线聚合类型：MINUTE-按分钟聚合（5个点），TEN_SECONDS-按10秒聚合（30个点），默认MINUTE",
            example = "MINUTE",
            allowableValues = {"MINUTE", "TEN_SECONDS"})
    private String aggregationType;
}
