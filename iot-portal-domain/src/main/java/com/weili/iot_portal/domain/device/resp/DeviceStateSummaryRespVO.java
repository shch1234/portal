package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.weili.iot_portal.common.serializer.TimestampLongSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    @Schema(description = "当前心跳", example = "2025-12-23 13:39:00")
    private String currentHeart;

    @Schema(description = "状态占比统计（饼图数据）")
    private StateRatioStatistics ratioStatistics;

    @Schema(description = "状态时间轴数据（时间轴图数据）")
    private List<StateTimeSegment> timelineData;

    /**
     * 状态占比统计（饼图数据）
     */
    @Schema(description = "状态占比统计")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateRatioStatistics {

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

    /**
     * 单个状态统计项
     */
    @Schema(description = "单个状态统计项")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateStatItem {

        @Schema(description = "状态名称", example = "待机")
        private String stateName;

        @Schema(description = "状态编码", example = "STANDBY")
        private String stateCode;

        @Schema(description = "时长（秒）", example = "3600")
        private Integer duration;

        @Schema(description = "占比", example = "0.30")
        private BigDecimal ratio;
    }

    /**
     * 状态时间段（时间轴图数据）
     */
    @Schema(description = "状态时间段")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateTimeSegment {

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
    }
}
