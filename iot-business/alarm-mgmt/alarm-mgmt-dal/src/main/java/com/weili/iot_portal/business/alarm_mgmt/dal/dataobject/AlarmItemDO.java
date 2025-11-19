package com.weili.iot_portal.business.alarm_mgmt.dal.dataobject;

import lombok.Data;

import java.io.Serializable;

/**
 * 报警项DO（数据对象）
 */
@Data
public class AlarmItemDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报警记录ID
     */
    private String id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备编号（威力编号）
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型名称
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称
     */
    private String deviceSubTypeName;

    /**
     * 报警号
     */
    private String alarmCode;

    /**
     * 报警内容
     */
    private String alarmText;

    /**
     * 报警级别：INFO、WARNING、ERROR、CRITICAL
     */
    private String alarmLevel;

    /**
     * 开始时间（毫秒时间戳）
     */
    private Long startTs;

    /**
     * 结束时间（毫秒时间戳，NULL表示报警中）
     */
    private Long endTs;

    /**
     * 持续时长（毫秒）
     */
    private Long durationMs;

    /**
     * 是否报警中
     */
    private Boolean isActive;

    /**
     * 开始班次日期（Date类型，MyBatis会自动转换）
     */
    private java.sql.Date startShiftDate;

    /**
     * 开始班次编码
     */
    private String startShiftCode;

    /**
     * 结束班次日期（报警进行中时为null，Date类型，MyBatis会自动转换）
     */
    private java.sql.Date endShiftDate;

    /**
     * 结束班次编码（报警进行中时为null）
     */
    private String endShiftCode;
}

