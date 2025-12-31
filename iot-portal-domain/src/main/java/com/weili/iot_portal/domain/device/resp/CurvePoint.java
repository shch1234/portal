package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 曲线数据点
 */
@Schema(description = "曲线数据点")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CurvePoint {

    @Schema(description = "时间（格式化后的时间字符串，如 14:30 或 14:30:15）", example = "14:30", requiredMode = Schema.RequiredMode.REQUIRED)
    private String time;

    @Schema(description = "数值（可以为 null）", example = "50.5", nullable = true)
    private BigDecimal value;
}

