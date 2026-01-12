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
 * 工厂级指标汇总表（按班次）：平均OEE、平均设备利用率等核心指标使用独立字段
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

    /** 班次开始时间（Unix时间戳） */
    private Long shiftStartTs;

    /** 班次结束时间（Unix时间戳） */
    private Long shiftEndTs;

    /** 平均OEE（整体设备效率） */
    private BigDecimal averageOee;
    /** 平均可用性 */
    private BigDecimal averageAvailability;
    /** 平均性能 */
    private BigDecimal averagePerformance;
    /** 平均质量 */
    private BigDecimal averageQuality;
    /** 平均利用率 */
    private BigDecimal averageUtilizationRate;
    /** 平均加工时长（小时） */
    private BigDecimal averageWorkingHours;
    /** 总加工数量 */
    private Integer totalProductionCount;
    /** 总合格数量 */
    private Integer totalQualifiedCount;
    /** 总计划停机时长（单位：秒） */
    private Integer totalPlannedDowntimeS;
    /** 总非计划停机时长（单位：秒） */
    private Integer totalUnplannedDowntimeS;
    /** 指标快照，任意结构，使用 JSON 存储 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metrics;
    /** 计算过程中的中间数据或参数快照，便于追溯与复算 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> calculationData;
    /** 参与计算的设备数量 */
    private Integer deviceCount;
    /** 是否已最终确定：1-已确定 0-待确定（班次结束后为1） */
    private Boolean isFinalized;
    /** 计算状态：PENDING-待计算 CALCULATED-已计算 RECALCULATED-已重算 FAILED-计算失败 */
    private String calculationStatus;
    /** 计算时间戳（秒，Unix时间戳） */
    private Long calculatedTime;
    /** 计算来源：SCHEDULED-定时任务 MANUAL-手动触发 RECALC-重算 */
    private String calculationSource;
    /** 数据完整度（有效数据设备数/总设备数) */
    private BigDecimal dataCompleteness;
    /** 重算时间戳（秒，Unix时间戳，NULL表示未重算） */
    private Long recalculatedAt;
    /** 重算原因（如参数修订、数据补全等） */
    private String recalculationReason;
    /** 重算次数 */
    private Integer recalculationCount;
}

