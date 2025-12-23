package com.weili.iot_portal.dal.dataobject.device;

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
 * 设备指标汇总数据对象（对应 device_metrics_summary 表）
 * 按班次存储设备OEE、开动率等核心指标
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_metrics_summary", autoResultMap = true)
public class DeviceMetricSummaryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -1760139988580895094L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

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
     * 班次时长（毫秒，计算字段，不对应数据库列）
     */
    @TableField(exist = false)
    private Long shiftDurationMs;

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
     * 参数快照（JSON，对应 parameter_snapshot 列）
     * 用于追溯指标计算时使用的参数版本
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameterSnapshot;

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
     * 数据完整度（有效数据时长/班次总时长，对应 data_completeness 列）
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


