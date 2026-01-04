package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @author luying
 * @className MetricDeviceListRespVO
 * @description
 * @date 2026-01-04 17:46
 **/
@Schema(description = "设备指标统计topN Response VO")
@Data
public class MetricDeviceDataRespVO {

    @Schema(description = "班次日期")
    private LocalDate shiftDate;

    @Schema(description = "班次编码")
    private Integer shiftCode;

    @Schema(description = "时间开动率")
    private List<MetricDeviceDataVO> availability;

    @Schema(description = "性能开动率")
    private List<MetricDeviceDataVO> performance;

    @Schema(description = "OEE指标")
    private List<MetricDeviceDataVO> oee;

    @Schema(description = "设备开动率")
    private List<MetricDeviceDataVO> utilizationRate;

    @Schema(description = "停机率")
    private List<MetricDeviceDataVO> downtimeRate;

    @Schema(description = "设备指标数据")
    @Data
    public static class MetricDeviceDataVO {

        @Schema(description = "设备ID")
        private String deviceId;

        @Schema(description = "设备编码")
        private String deviceCode;

        @Schema(description = "设备名称")
        private String deviceName;

        @Schema(description = "设备指标值")
        private BigDecimal value;

    }
}
