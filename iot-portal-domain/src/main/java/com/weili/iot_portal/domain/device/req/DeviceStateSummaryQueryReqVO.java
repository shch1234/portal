package com.weili.iot_portal.domain.device.req;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.weili.iot_portal.common.serializer.TimestampLongDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备状态汇总查询 Request VO
 */
@Schema(description = "设备状态汇总查询 Request VO")
@Data
public class DeviceStateSummaryQueryReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    @Schema(description = "开始时间（前端传 yyyy-MM-dd HH:mm:ss，后端自动转为毫秒时间戳）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-11-13 08:00:00")
    @NotNull(message = "开始时间不能为空")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long startTime;

    @Schema(description = "结束时间（前端传 yyyy-MM-dd HH:mm:ss，后端自动转为毫秒时间戳）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-11-13 20:00:00")
    @NotNull(message = "结束时间不能为空")
    @JsonDeserialize(using = TimestampLongDeserializer.class)
    private Long endTime;
}
