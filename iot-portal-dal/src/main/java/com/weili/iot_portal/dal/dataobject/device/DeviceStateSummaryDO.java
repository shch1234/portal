package com.weili.iot_portal.dal.dataobject.device;

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
 * 设备状态汇总数据对象（对应 device_state_summary 表）
 * 按班次存储设备状态时长与占比
 */
@Data
@TableName(value = "device_state_summary", autoResultMap = true)
public class DeviceStateSummaryDO implements Serializable {

    @Serial
    private static final long serialVersionUID = -1505674693530597958L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private Long deviceInfoId;

    /**
     * 所属厂区ID（关联 device_org_relation.id，冗余字段，优化查询性能，对应 org_factory_id 列）
     */
    private Long orgFactoryId;

    /**
     * 汇总日期（对应 summary_date 列）
     */
    private LocalDate summaryDate;

    /**
     * 班次编码（对应 shift_code 列，TINYINT UNSIGNED）
     * 编码映射：1-一班 2-二班 3-三班
     */
    private Integer shiftCode;

    /**
     * 班次开始时间戳（毫秒，Unix时间戳，对应 shift_start_ts 列）
     */
    private Long shiftStartTs;

    /**
     * 班次结束时间戳（毫秒，Unix时间戳，对应 shift_end_ts 列）
     */
    private Long shiftEndTs;

    /**
     * 状态统计详情（JSON，对应 state_statistics 列）
     * 每个状态的时长、占比、片段数等
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> stateStatistics;

    /**
     * 加工中时长（毫秒，冗余字段，对应 working_duration_s 列）
     */
    private Integer workingDurationS;

    /**
     * 待机时长（毫秒，冗余字段，对应 standby_duration_s 列）
     */
    private Integer standbyDurationS;

    /**
     * 故障时长（毫秒，冗余字段，对应 fault_duration_s 列）
     */
    private Integer faultDurationS;

    /**
     * 关机时长（毫秒，冗余字段，对应 shutdown_duration_s 列）
     */
    private Integer shutdownDurationS;

    /**
     * 未知状态时长（毫秒，冗余字段，对应 unknown_duration_s 列）
     */
    private Integer unknownDurationS;

    /**
     * 加工中占比（冗余字段，对应 working_ratio 列）
     */
    private BigDecimal workingRatio;

    /**
     * 待机占比（冗余字段，对应 standby_ratio 列）
     */
    private BigDecimal standbyRatio;

    /**
     * 故障占比（冗余字段，对应 fault_ratio 列）
     */
    private BigDecimal faultRatio;

    /**
     * 关机占比（冗余字段，对应 shutdown_ratio 列）
     */
    private BigDecimal shutdownRatio;

    /**
     * 未知状态占比（冗余字段，对应 unknown_ratio 列）
     */
    private BigDecimal unknownRatio;

    /**
     * 是否已最终确定：1-已确定 0-待确定（班次结束后为1，对应 is_finalized 列）
     */
    private Boolean isFinalized;

    /**
     * 计算时间戳（毫秒，Unix时间戳，对应 calculated_time 列）
     */
    private Long calculatedTime;

    /**
     * 计算来源（对应 calculation_source 列）
     * 如：SCHEDULED-定时任务 MANUAL-手动触发
     */
    private String calculationSource;

    /**
     * 数据完整度（有效数据时长/班次总时长，对应 data_completeness 列）
     */
    private BigDecimal dataCompleteness;

    /**
     * 缺失数据时长（毫秒，对应 missing_data_s 列）
     */
    private Integer missingDataS;
}


