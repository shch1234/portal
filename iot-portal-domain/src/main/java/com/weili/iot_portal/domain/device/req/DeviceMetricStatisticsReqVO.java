package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 设备指标统计查询 Request VO
 */
@Schema(description = "设备指标统计查询 Request VO")
@Data
public class DeviceMetricStatisticsReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull(message = "设备ID不能为空")
    private Long deviceInfoId;

    @Schema(description = "开始时间（前端传 yyyy-MM-dd；非必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate startTime;

    @Schema(description = "结束时间（前端传 yyyy-MM-dd；非必传）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate endTime;
}
