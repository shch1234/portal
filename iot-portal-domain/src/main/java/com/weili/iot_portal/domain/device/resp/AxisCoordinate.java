package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 轴坐标信息
 * 注意：所有字段都会被序列化，没有数据时返回 null
 */
@Schema(description = "轴坐标信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)  // 确保所有字段都被序列化，即使值为 null
public class AxisCoordinate {

    @Schema(description = "轴名称", example = "X")
    private String axisName;

    @Schema(description = "绝对坐标（可以为 null）", example = "1.00", nullable = true)
    private BigDecimal absolute;

    @Schema(description = "相对坐标（可以为 null）", example = "0.31", nullable = true)
    private BigDecimal relative;

    @Schema(description = "机械坐标（可以为 null）", example = "1.00", nullable = true)
    private BigDecimal machine;

    @Schema(description = "剩余坐标（可以为 null）", example = "0.00", nullable = true)
    private BigDecimal remaining;
}

