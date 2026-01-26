package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.weili.iot_portal.common.serializer.TimestampLongSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态时间段（时间轴图数据）
 */
@Schema(description = "状态时间段")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateTimeSegment {

    @Schema(description = "状态编码", example = "STANDBY")
    private String stateCode;

    @Schema(description = "状态名称", example = "待机")
    private String stateName;

    @Schema(description = "开始时间（毫秒时间戳，返回时自动转为 yyyy-MM-dd HH:mm:ss）", example = "1731470400000")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long startTime;

    @Schema(description = "结束时间（毫秒时间戳，返回时自动转为 yyyy-MM-dd HH:mm:ss）", example = "1731474000000")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long endTime;

    @Schema(description = "持续时间（秒）", example = "3600")
    private Long durationS;
}

