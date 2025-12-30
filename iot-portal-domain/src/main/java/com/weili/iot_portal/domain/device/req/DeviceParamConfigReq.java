package com.weili.iot_portal.domain.device.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备位置信息（嵌套对象）
 */
@Data
@Schema(description = "设备参数配置信息")
public class DeviceParamConfigReq {

    /**
     * 参数类型（对应 parameter_type 列）
     */
    @Schema(description = "参数类型", example = "THEORETICAL_CYCLE-理论节拍 PLANNED_DOWNTIME-计划停机时间")
    private String parameterType;

    /**
     * 参数值（数值型，对应 parameter_value 列）
     */
    @Schema(description = "参数值")
    private BigDecimal parameterValue;
}

