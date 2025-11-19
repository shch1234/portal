package com.weili.iot_portal.business.efficiency_mgmt.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工厂级效率指标趋势VO
 * 
 * <p>用于展示工厂级效率指标的历史趋势（按班次）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryMetricsTrendVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 平均OEE（百分比）
     */
    private BigDecimal averageOee;

    /**
     * 平均设备利用率（百分比）
     */
    private BigDecimal averageUtilizationRate;
}

