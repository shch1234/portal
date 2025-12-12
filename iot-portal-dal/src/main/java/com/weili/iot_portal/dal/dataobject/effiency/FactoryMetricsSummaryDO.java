package com.weili.iot_portal.dal.dataobject.effiency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 工厂级指标汇总数据对象（对应 factory_metric_summary 表）
 * 按班次存储工厂级平均OEE、平均设备利用率等核心指标
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "factory_metric_summary", autoResultMap = true)
public class FactoryMetricsSummaryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 工厂ID（关联 device_org_relation.id，unit_type=FACTORY，对应 org_factory_id 列）
     */
    private String orgFactoryId;

    /**
     * 班次日期（对应 shift_date 列）
     */
    private LocalDate shiftDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    /**
     * 班次开始时间戳（秒，Unix时间戳，对应 shift_start_ts 列）
     */
    private Long shiftStartTs;

    /**
     * 班次结束时间戳（秒，Unix时间戳，对应 shift_end_ts 列）
     */
    private Long shiftEndTs;

    /**
     * 班次总时长（毫秒，计算字段，不对应数据库列）
     */
    @TableField(exist = false)
    private Long shiftDurationMs;

    /**
     * 平均OEE（整体设备效率，对应 average_oee 列）
     */
    private BigDecimal averageOee;

    /**
     * 平均可用率（对应 average_availability 列）
     */
    private BigDecimal averageAvailability;

    /**
     * 平均性能率（对应 average_performance 列）
     */
    private BigDecimal averagePerformance;

    /**
     * 平均质量率（对应 average_quality 列）
     */
    private BigDecimal averageQuality;

    /**
     * 平均设备利用率（对应 average_utilization_rate 列）
     */
    private BigDecimal averageUtilizationRate;

    /**
     * 平均加工时长（小时，对应 average_working_hours 列）
     */
    private BigDecimal averageWorkingHours;

    /**
     * 总加工数量（对应 total_production_count 列）
     */
    private Integer totalProductionCount;

    /**
     * 总合格数量（对应 total_qualified_count 列）
     */
    private Integer totalQualifiedCount;

    /**
     * 总计划停机时长（秒，对应 total_planned_downtime_s 列）
     */
    private Integer totalPlannedDowntimeS;

    /**
     * 总非计划停机时长（秒，对应 total_unplanned_downtime_s 列）
     */
    private Integer totalUnplannedDowntimeS;

    /**
     * 完整指标数据（JSON，对应 metrics 列）
     * 包含核心指标和扩展指标，用于存储完整数据和动态扩展
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;

    /**
     * 计算依据的原始数据（JSON，对应 calculation_data 列）
     * 用于审计和重算
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> calculationData;

    /**
     * 参与计算的设备数量（对应 device_count 列）
     */
    private Integer deviceCount;

    /**
     * 是否已最终确定：1-已确定 0-待确定（班次结束后为1，对应 is_finalized 列）
     */
    private Boolean isFinalized;

    /**
     * 计算状态（对应 calculation_status 列）
     * 如：PENDING-待计算 CALCULATED-已计算 RECALCULATED-已重算 FAILED-计算失败
     */
    private String calculationStatus;

    /**
     * 计算时间戳（秒，Unix时间戳，对应 calculated_time 列）
     */
    private Long calculatedTime;

    /**
     * 计算来源（对应 calculation_source 列）
     * 如：SCHEDULED-定时任务 MANUAL-手动触发 RECALC-重算
     */
    private String calculationSource;

    /**
     * 数据完整度（有效数据设备数/总设备数，对应 data_completeness 列）
     */
    private BigDecimal dataCompleteness;

    /**
     * 重算时间戳（秒，Unix时间戳，NULL表示未重算，对应 recalculated_at 列）
     */
    private Long recalculatedAt;

    /**
     * 重算原因（如参数修订、数据补全等，对应 recalculation_reason 列）
     */
    private String recalculationReason;

    /**
     * 重算次数（对应 recalculation_count 列）
     */
    private Integer recalculationCount;
}

