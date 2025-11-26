package com.weili.iot_portal.dal.dataobject.efficiency;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 设备效率指标DO（数据对象）
 */
@Data
public class DeviceEfficiencyMetricDO implements Serializable {

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
     * 设备类型名称
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称
     */
    private String deviceSubTypeName;

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
     * 班次编码
     */
    private String shiftCode;

    /**
     * 指标代码（从metrics JSONB中提取）
     */
    private String metricCode;

    /**
     * 指标值（从metrics JSONB中提取，转换为BigDecimal）
     */
    private BigDecimal metricValue;

    /**
     * 是否已最终确定
     */
    private Boolean isFinalized;
}

