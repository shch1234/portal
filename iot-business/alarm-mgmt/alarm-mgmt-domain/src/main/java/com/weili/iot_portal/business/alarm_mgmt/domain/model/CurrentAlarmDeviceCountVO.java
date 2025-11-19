package com.weili.iot_portal.business.alarm_mgmt.domain.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前报警设备数量VO
 */
@Data
public class CurrentAlarmDeviceCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID
     */
    private String factoryId;

    /**
     * 工厂名称
     */
    private String factoryName;

    /**
     * 车间ID（可选，如果按车间查询）
     */
    private String workshopId;

    /**
     * 车间名称（可选，如果按车间查询）
     */
    private String workshopName;

    /**
     * 当前报警设备数量（去重后的设备个数）
     */
    private Integer deviceCount;
}

