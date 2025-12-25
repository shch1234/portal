package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 几何补偿（Geometry）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "几何补偿")
public class GeometryCompensation {

    @Schema(description = "X轴几何补偿（发那科/科德=X，西门子=L1）", example = "0.5")
    private BigDecimal offsetX;

    @Schema(description = "Y轴几何补偿（发那科/科德=Y，西门子=L2）", example = "-0.3")
    private BigDecimal offsetY;

    @Schema(description = "Z轴几何补偿（发那科/科德=Z，西门子=L3）", example = "10.2")
    private BigDecimal offsetZ;

    @Schema(description = "半径几何补偿（发那科/科德=R，西门子=R1）", example = "0.0")
    private BigDecimal offsetR;
}

