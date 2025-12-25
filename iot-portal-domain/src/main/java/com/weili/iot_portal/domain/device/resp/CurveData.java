package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 曲线数据
 */
@Schema(description = "曲线数据")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurveData {

    @Schema(description = "曲线数据点列表")
    private List<CurvePoint> points;

    @Schema(description = "当前值（最新值）")
    private BigDecimal currentValue;
}

