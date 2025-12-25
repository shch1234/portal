package com.weili.iot_portal.domain.device.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备轴标签信息 Response VO
 */
@Schema(description = "设备轴标签信息 Response VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAxisRespVO {

    @Schema(description = "主轴信息")
    private SpindleInfo spindleInfo;

    @Schema(description = "轴坐标标签信息列表")
    private List<AxisCoordinate> axisCoordinates;

    @Schema(description = "倍率值（百分比）", example = "50")
    private Integer ratio;
}
