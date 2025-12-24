package com.weili.iot_portal.domain.device.req;

import com.weili.iot_portal.common.serializer.TimestampLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备刀具记录查询请求VO
 */
@Data
@Schema(description = "设备刀具记录查询请求")
public class DeviceToolRecordQueryReqVO {

    @NotNull(message = "设备ID不能为空")
    @Schema(description = "设备ID", required = true, example = "1234567890")
    private Long deviceInfoId;

    @Schema(description = "开始时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 08:00:00")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long startTime;

    @Schema(description = "结束时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 18:00:00")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long endTime;

    @Schema(description = "限制返回条数", example = "100")
    private Integer limit;
}
