package com.weili.iot_portal.business.digital_screen.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工厂级效率指标趋势VO（数字大屏用）
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

