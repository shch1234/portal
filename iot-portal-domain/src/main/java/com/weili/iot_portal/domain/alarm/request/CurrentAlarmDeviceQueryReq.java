package com.weili.iot_portal.domain.alarm.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前报警设备查询请求
 */
@Data
public class CurrentAlarmDeviceQueryReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID（必填）
     */
    private String factoryId;

    /**
     * 车间ID（可选，如果不填则查询该工厂下所有车间的报警设备）
     */
    private String workshopId;

    /**
     * 分页页码（默认1）
     */
    private Integer pageNo = 1;

    /**
     * 分页大小（默认10）
     */
    private Integer pageSize = 10;
}

