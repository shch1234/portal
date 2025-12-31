package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备产量统计查询 Request VO
 */
@Schema(description = "设备产量统计查询 Request VO")
@Data
public class DeviceProductionStatisticsReqVO {

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789")
    @NotNull(message = "设备ID不能为空")
    private Long deviceInfoId;

    @Schema(description = "统计时间范围：WEEK-近一周 MONTH-近一个月", requiredMode = Schema.RequiredMode.REQUIRED, example = "WEEK")
    private String timeRange;
}
