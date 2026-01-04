package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备指标统计查询 Request VO
 */
@Schema(description = "设备指标统计查询 Request VO")
@Data
public class MetricStatisticsReqVO {

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "工厂ID")
    private Long orgFactoryId;

    @Schema(description = "开始时间（前端传 yyyy-MM-dd；非必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate startTime;

    @Schema(description = "结束时间（前端传 yyyy-MM-dd；非必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate endTime;
}
