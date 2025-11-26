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
 * 报警历史 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_alarm_history", autoResultMap = true)
public class AlarmHistoryDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 4052452018037289381L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String deviceId;

    private String alarmCode;

    private String alarmText;

    private String alarmLevel;

    private Long startTs;

    private Long endTs;

    private Long durationMs;

    private Boolean isActive;

    private LocalDate startShiftDate;

    private String startShiftCode;

    private LocalDate endShiftDate;

    private String endShiftCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;
}


