package com.weili.iot_portal.domain.device.req;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.weili.iot_portal.common.serializer.TimestampLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备产量统计查询 Request VO
 */
@Schema(description = "设备产量统计查询 Request VO")
@Data
public class DeviceProductionStatisticsReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull(message = "设备ID不能为空")
    private Long deviceInfoId;

    @Schema(description = "开始时间（前端传 yyyy-MM-dd HH:mm:ss，后端自动转为毫秒时间戳；非必传，不传则使用当前班次）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "2024-11-13 08:00:00")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long startTime;

    @Schema(description = "结束时间（前端传 yyyy-MM-dd HH:mm:ss，后端自动转为毫秒时间戳；非必传，不传则使用当前班次）", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "2024-11-13 20:00:00")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long endTime;
}
