package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个状态统计项
 */
@Schema(description = "单个状态统计项")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateStatItem {

    @Schema(description = "状态名称", example = "待机")
    private String stateName;

    @Schema(description = "状态编码", example = "STANDBY")
    private String stateCode;

    @Schema(description = "时长（秒）", example = "3600")
    private Integer duration;

    @Schema(description = "占比", example = "0.30")
    private BigDecimal ratio;
}

