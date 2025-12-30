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

    /** 所属工厂 ID */
    private Long orgFactoryId;

    /** 班次日期（YYYY-MM-DD） - 班次维度的日期标签 */
    private LocalDate shiftDate;

    /** 班次编码（可为字符串或数值的字符串化表示，取决于班次配置） */
    private String shiftCode;

    /** 班次开始时间（epoch millis） */
    private Long shiftStartTs;

    /** 班次结束时间（epoch millis） */
    private Long shiftEndTs;

    // 核心指标（按班次聚合的统计值）
    /** 平均 OEE（0..1 或 0..100，取决于上层约定） */
    private BigDecimal averageOee;
    /** 平均可用性 */
    private BigDecimal averageAvailability;
    /** 平均性能 */
    private BigDecimal averagePerformance;
    /** 平均质量 */
    private BigDecimal averageQuality;
    /** 平均利用率 */
    private BigDecimal averageUtilizationRate;
    /** 平均工时（小时） */
    private BigDecimal averageWorkingHours;
    /** 产量（总计） */
    private Integer totalProductionCount;
    /** 合格产量（总计） */
    private Integer totalQualifiedCount;
    /** 计划停机时长（秒）——保留历史字段命名（单位：秒） */
    private Integer totalPlannedDowntimeS;
    /** 非计划停机时长（秒）——保留历史字段命名（单位：秒） */
    private Integer totalUnplannedDowntimeS;

    // 扩展/审计数据（JSON 字段）
    /** 指标快照，任意结构，使用 JSON 存储 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;
    /** 计算过程中的中间数据或参数快照，便于追溯与复算 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> calculationData;

    /** 统计涉及的设备数量 */
    private Integer deviceCount;
    /** 是否为最终汇总（true 表示完成且无需再复算） */
    private Boolean isFinalized;
    /** 计算状态（可用于人工或自动复算流程的标识） */
    private String calculationStatus;
    /** 计算时间（epoch millis） */
    private Long calculatedTime;
    /** 计算来源（例如 SCHEDULED、COMPENSATION、MANUAL 等） */
    private String calculationSource;
    /** 数据完整度（0..1） */
    private BigDecimal dataCompleteness;
    /** 最近一次复算时间（epoch millis） */
    private Long recalculatedAt;
    /** 最近一次复算原因（用于审计） */
    private String recalculationReason;
    /** 复算次数统计 */
    private Integer recalculationCount;
}

