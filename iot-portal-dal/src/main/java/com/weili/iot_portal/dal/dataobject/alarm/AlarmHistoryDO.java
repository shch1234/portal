package com.weili.iot_portal.dal.dataobject.alarm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;
import java.util.Map;

/**
 * 设备报警历史数据对象（对应 device_alarm_history 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_alarm_history", autoResultMap = true)
public class AlarmHistoryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 4052452018037289381L;

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 设备ID（关联 device_info.id，对应 device_info_id 列）
     * 可通过 device_info.tb_device_id 查询 ThingsBoard 获取租户信息
     */
    private String deviceInfoId;

    /**
     * 报警编号（对应 alarm_code 列）
     */
    private String alarmCode;

    /**
     * 报警内容（对应 alarm_text 列）
     */
    private String alarmText;

    /**
     * 报警级别（对应 alarm_level 列）
     * 如：INFO-信息 WARNING-警告 ERROR-错误 CRITICAL-严重
     */
    private String alarmLevel;

    /**
     * 报警开始时间戳（秒，Unix时间戳，对应 start_ts 列）
     */
    private Long startTs;

    /**
     * 报警结束时间戳（秒，Unix时间戳，NULL表示报警中，对应 end_ts 列）
     */
    private Long endTs;

    /**
     * 持续时长（秒，对应 duration_s 列）
     */
    private Integer durationS;

    /**
     * 是否报警中：1-报警中 0-已解除（对应 is_active 列）
     */
    private Boolean isActive;

    /**
     * 报警开始班次日期（对应 start_shift_date 列）
     */
    private LocalDate startShiftDate;

    /**
     * 报警开始班次编码（对应 start_shift_code 列）
     */
    private String startShiftCode;

    /**
     * 报警结束班次日期（NULL表示报警中，对应 end_shift_date 列）
     */
    private LocalDate endShiftDate;

    /**
     * 报警结束班次编码（NULL表示报警中，对应 end_shift_code 列）
     */
    private String endShiftCode;

    /**
     * 扩展属性（JSON，对应 properties 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;
}


