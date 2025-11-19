package com.weili.iot_portal.business.efficiency_mgmt.domain.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 设备效率指标VO
 * 
 * <p>用于展示指定指标在指定班次时，所有设备的指标值
 * 支持排序功能
 */
@Data
public class DeviceEfficiencyMetricVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备编号（威力编号）
     */
    private String deviceCode;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 设备子类型
     */
    private String deviceSubType;

    /**
     * 车间ID
     */
    private String workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 班次日期
     */
    private String shiftDate;

    /**
     * 班次编码（SHIFT_1、SHIFT_2、SHIFT_3）
     */
    private String shiftCode;

    /**
     * 指标代码（如：oee、timeAvailability等）
     */
    private String metricCode;

    /**
     * 指标名称（如：OEE、时间开动率等）
     */
    private String metricName;

    /**
     * 指标值（百分比，如：85.5表示85.5%）
     */
    private BigDecimal metricValue;

    /**
     * 指标单位（如：%）
     */
    private String metricUnit;

    /**
     * 是否已最终确定（班次结束后为true）
     */
    private Boolean isFinalized;
}

