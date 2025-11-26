package com.weili.iot_portal.domain.digital;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工厂级效率指标VO（数字大屏用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryMetricsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID
     */
    private String factoryId;

    /**
     * 工厂名称
     */
    private String factoryName;

    /**
     * 当前平均OEE（百分比）
     */
    private BigDecimal currentAverageOee;

    /**
     * 当前平均设备利用率（百分比）
     */
    private BigDecimal currentAverageUtilizationRate;

    private BigDecimal averageOee;

    private BigDecimal averageUtilizationRate;

    /**
     * 历史趋势数据（过去7天，按班次）
     */
    private List<FactoryMetricsTrendVO> historyTrend;

}

