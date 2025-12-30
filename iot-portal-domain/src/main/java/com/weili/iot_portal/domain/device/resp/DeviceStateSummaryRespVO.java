package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备状态汇总 Response VO
 */
@Schema(description = "设备状态汇总 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStateSummaryRespVO {

    @Schema(description = "当前状态", example = "STANDBY")
    private String currentState;

    @Schema(description = "当前心跳 true:有 false：无")
    private boolean currentHeart;

    @Schema(description = "状态占比统计（饼图数据）")
    private StateRatioStatistics ratioStatistics;

    @Schema(description = "状态时间轴数据（时间轴图数据）")
    private List<StateTimeSegment> timelineData;
}
