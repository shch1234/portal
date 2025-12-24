package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备轴标签信息 Response VO
 */
@Schema(description = "设备轴标签信息 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAxisRespVO {

    @Schema(description = "主轴信息")
    private SpindleInfo spindleInfo;

    @Schema(description = "轴坐标标签信息列表")
    private List<AxisCoordinate> axisCoordinates;

    @Schema(description = "倍率值（百分比）", example = "50")
    private Integer ratio;

    /**
     * 主轴信息
     */
    @Schema(description = "主轴信息")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpindleInfo {

        @Schema(description = "负载曲线数据")
        private CurveData loadCurve;

        @Schema(description = "转速曲线数据")
        private CurveData rpmCurve;

        @Schema(description = "进给曲线数据")
        private CurveData feedCurve;
    }

    /**
     * 曲线数据
     */
    @Schema(description = "曲线数据")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurveData {

        @Schema(description = "曲线数据点列表")
        private List<CurvePoint> points;

        @Schema(description = "当前值（最新值）")
        private BigDecimal currentValue;
    }

    /**
     * 曲线数据点
     */
    @Schema(description = "曲线数据点")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurvePoint {

        @Schema(description = "时间（格式化后的时间字符串，如 14:30 或 14:30:15）", example = "14:30")
        private String time;

        @Schema(description = "数值", example = "50.5")
        private BigDecimal value;
    }

    /**
     * 轴坐标信息
     */
    @Schema(description = "轴坐标信息")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AxisCoordinate {

        @Schema(description = "轴名称", example = "X")
        private String axisName;

        @Schema(description = "绝对坐标", example = "1.00")
        private BigDecimal absolute;

        @Schema(description = "相对坐标", example = "0.31")
        private BigDecimal relative;

        @Schema(description = "机械坐标", example = "1.00")
        private BigDecimal machine;

        @Schema(description = "剩余坐标", example = "0.00")
        private BigDecimal remaining;
    }
}
