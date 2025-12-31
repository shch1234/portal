package com.weili.iot_portal.domain.device.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主轴信息
 */
@Schema(description = "主轴信息")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SpindleInfo {

    @Schema(description = "负载曲线数据")
    private CurveData loadCurve;

    @Schema(description = "转速曲线数据")
    private CurveData rpmCurve;

    @Schema(description = "进给曲线数据")
    private CurveData feedCurve;
}

