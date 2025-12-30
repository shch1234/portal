package com.weili.iot_portal.domain.device.req;

import com.weili.basic.common.model.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备刀具记录查询请求VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备刀具记录查询请求")
public class DeviceToolRecordQueryReqVO extends PageParam {

    @NotNull(message = "设备ID不能为空")
    @Schema(description = "设备ID", required = true, example = "1234567890")
    private Long deviceId;
}
