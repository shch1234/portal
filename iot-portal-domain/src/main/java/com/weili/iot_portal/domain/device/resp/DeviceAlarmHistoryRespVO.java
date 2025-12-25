package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.weili.iot_portal.common.serializer.TimestampSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备告警历史响应VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备告警历史响应")
public class DeviceAlarmHistoryRespVO {

    @Schema(description = "报警编号", example = "ALM001")
    private String alarmCode;

    @Schema(description = "报警内容", example = "主轴温度过高")
    private String alarmText;

    @Schema(description = "开始时间（yyyy-MM-dd HH:mm:ss格式）", example = "2024-11-13 08:30:00")
    @JsonSerialize(using = TimestampSerializer.class)
    private Long startTs;

    @Schema(description = "结束时间（yyyy-MM-dd HH:mm:ss格式，NULL表示报警中）", example = "2024-11-13 08:35:00")
    @JsonSerialize(using = TimestampSerializer.class)
    private Long endTs;

    @Schema(description = "持续时间（格式化字符串，如：5分钟）", example = "5分钟")
    private String duration;

    @Schema(description = "持续时间（秒）", example = "300")
    private Integer durationS;

    @Schema(description = "是否报警中：1-报警中 0-已结束", example = "0")
    private Integer isActive;
}
