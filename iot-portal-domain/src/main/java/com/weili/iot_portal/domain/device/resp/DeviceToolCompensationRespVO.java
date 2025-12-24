package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


/**
 * 刀具补偿项（每一行代表一个刀补号的数据）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "刀具补偿项")
public class DeviceToolCompensationRespVO {

    @Schema(description = "刀补号", example = "T01")
    private String toolHolderNo;

    @Schema(description = "几何补偿")
    private GeometryCompensation geometry;

    @Schema(description = "磨损补偿")
    private WearCompensation wear;

    /**
     * 几何补偿（Geometry）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "几何补偿")
    public static class GeometryCompensation {

        @Schema(description = "X轴几何补偿（发那科/科德=X，西门子=L1）", example = "0.5")
        private BigDecimal offsetX;

        @Schema(description = "Y轴几何补偿（发那科/科德=Y，西门子=L2）", example = "-0.3")
        private BigDecimal offsetY;

        @Schema(description = "Z轴几何补偿（发那科/科德=Z，西门子=L3）", example = "10.2")
        private BigDecimal offsetZ;

        @Schema(description = "半径几何补偿（发那科/科德=R，西门子=R1）", example = "0.0")
        private BigDecimal offsetR;
    }

    /**
     * 磨损补偿（Wear）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "磨损补偿")
    public static class WearCompensation {

        @Schema(description = "X轴磨损补偿", example = "0.1")
        private BigDecimal compX;

        @Schema(description = "Y轴磨损补偿", example = "0.2")
        private BigDecimal compY;

        @Schema(description = "Z轴磨损补偿", example = "0.0")
        private BigDecimal compZ;

        @Schema(description = "半径磨损补偿", example = "0.0")
        private BigDecimal compR;
    }
}
