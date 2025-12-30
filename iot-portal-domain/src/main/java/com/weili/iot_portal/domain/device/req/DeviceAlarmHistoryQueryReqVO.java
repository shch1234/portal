package com.weili.iot_portal.domain.device.req;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.weili.basic.common.model.PageParam;
import com.weili.iot_portal.common.serializer.TimestampLongSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备告警历史查询请求VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备告警查询请求")
public class DeviceAlarmHistoryQueryReqVO extends PageParam {

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "报警状态：1-报警中 0-已解除", example = "1")
    private Integer isActive;

    @Schema(description = "开始时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 08:00:00")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long startTime;

    @Schema(description = "结束时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 18:00:00")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long endTime;
}
