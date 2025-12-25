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
    private StateStatItem standby;

    @Schema(description = "加工状态统计")
    private StateStatItem working;

    @Schema(description = "关机状态统计")
    private StateStatItem shutdown;

    @Schema(description = "故障状态统计")
    private StateStatItem fault;

    @Schema(description = "总时长（秒）")
    private Integer totalDuration;
}

