package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 轴坐标信息
 */
@Schema(description = "轴坐标信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AxisCoordinate {

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

