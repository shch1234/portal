package com.weili.iot_portal.domain.alarm;

import lombok.Data;

import java.io.Serializable;

/**
 * 报警设备项VO（列表中的单个设备）
 */
@Data
public class AlarmDeviceItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
     * 车间名称
     */
    private String workshopName;

    /**
     * 当前报警数量（该设备正在报警的个数）
     */
    private Integer alarmCount;

    /**
     * 最新报警开始时间（毫秒时间戳）
     */
    private Long latestAlarmStartTs;
}

