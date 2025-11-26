package com.weili.iot_portal.domain.efficiency.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 效率指标查询请求
 * 
 * <p>用于查询指定指标在指定班次时，所有设备的指标值
 * 支持排序和分页
 */
@Data
public class EfficiencyMetricQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID（必填，用于数据隔离）
     */
    private String factoryId;

    /**
     * 车间ID（可选，如果不填则查询该工厂下所有车间的设备）
     */
    private String workshopId;

    /**
     * 指标代码（必填）
     * <p>可选值：oee、timeAvailability、performanceRate、equipmentAvailability、downtimeRate
     */
    private String metricCode;

    /**
     * 班次日期（可选，格式：yyyy-MM-dd）
     * <p>如果不填，则自动使用当前班次日期（实时查询）
     */
    private String shiftDate;

    /**
     * 班次编码（可选）
     * <p>可选值：SHIFT_1（早班）、SHIFT_2（晚班）、SHIFT_3（夜班）
     * <p>如果不填，则自动使用当前班次编码（实时查询）
     */
    private String shiftCode;

    /**
     * 排序字段（可选，默认按指标值降序）
     * <p>可选值：metricValue（按指标值）、deviceCode（按设备编号）
     * <p>默认：metricValue
     */
    private String sortBy = "metricValue";

    /**
     * 排序方向（可选，默认降序）
     * <p>可选值：ASC（升序）、DESC（降序）
     * <p>默认：DESC
     */
    private String sortDirection = "DESC";

    /**
     * 分页页码（默认1）
     */
    private Integer pageNo = 1;

    /**
     * 分页大小（默认10）
     */
    private Integer pageSize = 10;
}

