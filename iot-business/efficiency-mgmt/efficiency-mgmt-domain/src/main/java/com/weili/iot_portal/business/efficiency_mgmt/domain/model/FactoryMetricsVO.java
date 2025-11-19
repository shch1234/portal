package com.weili.iot_portal.business.efficiency_mgmt.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 工厂级效率指标VO
 * 
 * <p>用于数字大屏展示工厂级效率指标（平均OEE、平均设备利用率等）
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
     * 班次日期
     */
    private String shiftDate;

    /**
     * 班次编码
     */
    private String shiftCode;

    /**
     * 班次名称
     */
    private String shiftName;

    /**
     * 平均OEE（百分比，如：85.5表示85.5%）
     */
    private BigDecimal averageOee;

    /**
     * 平均设备利用率（百分比，如：90.0表示90.0%）
     */
    private BigDecimal averageUtilizationRate;

    /**
     * 参与计算的设备数量
     */
    private Integer deviceCount;

    /**
     * 是否已最终确定（班次结束后为true）
     */
    private Boolean isFinalized;

    /**
     * 历史趋势数据（过去7天）
     */
    private List<FactoryMetricsTrendVO> historyTrend;
}

