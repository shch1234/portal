package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态占比统计（饼图数据）
 */
@Schema(description = "状态占比统计")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateRatioStatistics {

    @Schema(description = "待机状态统计")
    private Integer standbyDur;

    @Schema(description = "加工状态统计")
    private Integer workingDur;

    @Schema(description = "关机状态统计")
    private Integer shutdownDur;

    @Schema(description = "故障状态统计")
    private Integer faultDur;
}

