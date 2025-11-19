package com.weili.iot_portal.business.digital_screen.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 厂区设备状态监测数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryStatusSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String factoryId;

    private String factoryName;

    private Long totalCount;

    private Long runningCount;

    private Long standbyCount;

    private Long faultCount;

    private Long shutdownCount;

    private BigDecimal runningRatio;

    private BigDecimal standbyRatio;

    private BigDecimal faultRatio;

    private BigDecimal shutdownRatio;
}


