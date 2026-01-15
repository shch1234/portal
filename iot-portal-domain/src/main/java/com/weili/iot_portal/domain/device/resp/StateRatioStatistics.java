package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 状态占比统计（饼图数据）
 */
@Schema(description = "状态占比统计")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateRatioStatistics {

    @Schema(description = "待机状态时长（秒）")
    private Integer standbyDur;

    @Schema(description = "加工状态时长（秒）")
    private Integer workingDur;

    @Schema(description = "关机状态时长（秒）")
    private Integer shutdownDur;

    @Schema(description = "故障状态时长（秒）")
    private Integer faultDur;

    @Schema(description = "未知状态时长（秒）")
    private Integer unknownDur;

    @Schema(description = "待机状态占比（%，保留1位小数）", example = "25.0")
    private BigDecimal standbyRatio;

    @Schema(description = "加工状态占比（%，保留1位小数）", example = "50.0")
    private BigDecimal workingRatio;

    @Schema(description = "关机状态占比（%，保留1位小数）", example = "15.0")
    private BigDecimal shutdownRatio;

    @Schema(description = "故障状态占比（%，保留1位小数）", example = "10.0")
    private BigDecimal faultRatio;

    @Schema(description = "未知状态占比（%，保留1位小数）", example = "5.0")
    private BigDecimal unknownRatio;
}


