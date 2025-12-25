package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.weili.iot_portal.common.serializer.TimestampLongSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刀具记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "刀具列表")
public class ToolRecord {

    @Schema(description = "刀号（T01、T02等）", example = "T01")
    private String toolNo;

    @Schema(description = "刀套号", example = "M01")
    private String toolMagazineNo;

    @Schema(description = "开始使用时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 08:30:00")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long startTs;

    @Schema(description = "结束使用时间（yyyy-MM-dd HH:mm:ss格式，NULL表示使用中）", example = "2024-11-13 10:30:00")
    @JsonSerialize(using = TimestampLongSerializer.class)
    private Long endTs;

    @Schema(description = "持续时长（格式化字符串，如：2小时30分钟）", example = "2小时30分钟")
    private String duration;

    @Schema(description = "持续时长（毫秒）", example = "9000000")
    private Long durationMs;
}

