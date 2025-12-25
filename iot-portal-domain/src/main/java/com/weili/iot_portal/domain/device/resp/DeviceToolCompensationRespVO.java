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
}
