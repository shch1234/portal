package com.weili.iot_portal.business.alarm_mgmt.dal.dataobject;

import lombok.Data;

import java.io.Serializable;

/**
 * 报警设备统计 DO（用于统计查询结果）
 */
@Data
public class AlarmDeviceStatisticsDO implements Serializable {

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
     * 设备类型名称
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称
     */
    private String deviceSubTypeName;

    /**
     * 车间ID
     */
    private String workshopId;

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

