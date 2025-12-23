package com.weili.iot_portal.dal.dataobject.factory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 工厂级指标汇总（按班次）DO
 * 对应表：factory_metric_summary
 */
@Data
@TableName(value = "factory_metric_summary", autoResultMap = true)
public class FactoryMetricSummaryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 879654321L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orgFactoryId;
    private LocalDate shiftDate;
    private String shiftCode;
    private Long shiftStartTs;
    private Long shiftEndTs;

    // 核心指标
    private BigDecimal averageOee;
    private BigDecimal averageAvailability;
    private BigDecimal averagePerformance;
    private BigDecimal averageQuality;
    private BigDecimal averageUtilizationRate;
    private BigDecimal averageWorkingHours;
    private Integer totalProductionCount;
    private Integer totalQualifiedCount;
    private Integer totalPlannedDowntimeS;
    private Integer totalUnplannedDowntimeS;

    // 扩展/审计数据
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> calculationData;

    private Integer deviceCount;
    private Boolean isFinalized;
    private String calculationStatus;
    private Long calculatedTime;
    private String calculationSource;
    private BigDecimal dataCompleteness;
    private Long recalculatedAt;
    private String recalculationReason;
    private Integer recalculationCount;
}

