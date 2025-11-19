package com.weili.iot_portal.business.alarm_mgmt.domain.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 报警项VO（报警列表中的单条报警记录）
 * 
 * <p>对应需求：4.3.2 报警列表
 * - 字段：设备编号、设备类型/子类型、报警号、报警内容、开始时间、结束时间、持续时间、是否报警中
 */
@Data
public class AlarmItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报警记录ID
     */
    private String alarmId;

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
     * 设备类型
     */
    private String deviceType;

    /**
     * 设备子类型
     */
    private String deviceSubType;

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
    private Long startTime;

    /**
     * 结束时间（毫秒时间戳）
     * <p>进行中的报警：为null，前端应显示"-"
     */
    private Long endTime;

    /**
     * 持续时间（毫秒）
     * <p>进行中的报警：实时计算（当前时间 - 开始时间）
     */
    private Long durationMs;

    /**
     * 是否报警中
     * <p>true：报警进行中；false：报警已结束
     */
    private Boolean isActive;

    /**
     * 开始班次日期
     */
    private String startShiftDate;

    /**
     * 开始班次编码
     */
    private String startShiftCode;

    /**
     * 结束班次日期（报警进行中时为null）
     */
    private String endShiftDate;

    /**
     * 结束班次编码（报警进行中时为null）
     */
    private String endShiftCode;
}

