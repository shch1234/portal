package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * @author luying
 * @className MetricDeviceListReqVO
 * @description
 * @date 2026-01-04 18:00
 **/
@Data
public class MetricDeviceDataReqVO {

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "车间id")
    private Long orgFactoryId;

    @Schema(description = "时间 yyyy-MM-dd，默认系统当前时间")
    private LocalDate startTime;

    @Schema(description = "每日的topN，默认5")
    private Integer top;

    @Schema(description = "班次编码")
    private Integer shiftCode;
}
