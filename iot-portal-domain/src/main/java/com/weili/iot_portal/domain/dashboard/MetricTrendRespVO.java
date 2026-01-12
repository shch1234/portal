package com.weili.iot_portal.domain.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 指标趋势数据响应对象
 *
 * @author luying
 * @date 2026-01-12
 */
@Data
@Schema(description = "指标趋势数据响应对象")
public class MetricTrendRespVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "横坐标（时间点列表）")
    private List<String> time;

    @Schema(description = "纵坐标（指标值列表）")
    private List<BigDecimal> value;

    @Schema(description = "指标类型：OEE-平均OEE, UTILIZATION-平均设备利用率")
    private String metricType;
}
