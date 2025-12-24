package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备刀具补偿查询请求VO
 */
@Data
@Schema(description = "设备刀具补偿查询请求")
public class DeviceToolCompensationQueryReqVO {

    @NotNull(message = "设备ID不能为空")
    @Schema(description = "设备ID", required = true, example = "1234567890")
    private Long deviceInfoId;

    @Schema(description = "工厂ID（可选，用于过滤）", example = "1001")
    private Long orgFactoryId;
}
