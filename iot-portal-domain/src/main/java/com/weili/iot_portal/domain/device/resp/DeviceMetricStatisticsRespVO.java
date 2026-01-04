package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备指标统计 Response VO
 */
@Schema(description = "设备指标统计 Response VO")
@Data
public class DeviceMetricStatisticsRespVO {

    @Schema(description = "当前指标值（根据查询类型返回对应指标的当前值，百分比形式 0-100）", example = "50.0")
    private MetricDetailVO currentMetricValue;

    @Schema(description = "指标明细列表（按日期展示）")
    private List<MetricDetailVO> metricDetails;

    /**
     * 指标明细 VO
     */
    @Schema(description = "指标明细 VO")
    @Data
    public static class MetricDetailVO {

        @Schema(description = "日期标识（格式：MM-dd）", example = "12-23")
        private String dateLabel;

        @Schema(description = "时间开动率（可用率，百分比形式 0-100）", example = "85.5")
        private BigDecimal availability;

        @Schema(description = "性能开动率（性能率，百分比形式 0-100）", example = "92.3")
        private BigDecimal performance;

        @Schema(description = "OEE（整体设备效率，百分比形式 0-100）", example = "78.9")
        private BigDecimal oee;

        @Schema(description = "设备开动率（设备利用率，百分比形式 0-100）", example = "88.0")
        private BigDecimal utilizationRate;

        @Schema(description = "停机率（百分比形式 0-100）", example = "12.0")
        private BigDecimal downtimeRate;
    }
}
