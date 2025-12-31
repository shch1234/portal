package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 曲线数据
 * 注意：所有字段都会被序列化，即使值为 null
 */
@Schema(description = "曲线数据")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)  // 确保所有字段都被序列化，即使值为 null
public class CurveData {

    @Schema(description = "曲线数据点列表")
    private List<CurvePoint> points;

    @Schema(description = "当前值（最新值，可以为 null）", nullable = true)
    private BigDecimal currentValue;
}

