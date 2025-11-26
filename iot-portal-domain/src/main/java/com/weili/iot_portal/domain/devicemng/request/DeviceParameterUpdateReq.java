package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新设备参数请求
 */
@Data
public class DeviceParameterUpdateReq {

    private String deviceId;

    @NotNull(message = "理论节拍不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "理论节拍必须大于0")
    private Double theoreticalCycleHours;

    @NotNull(message = "计划停机不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "计划停机必须大于0")
    private Double plannedDowntimeHours;

    private String remark;
}


