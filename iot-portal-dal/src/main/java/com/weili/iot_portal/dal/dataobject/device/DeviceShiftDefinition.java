package com.weili.iot_portal.dal.dataobject.device;

import lombok.Data;

/**
 * 班次定义
 * 用于设备班次配置中的班次信息
 */
@Data
public class DeviceShiftDefinition {
    /**
     * 班次编码：1、2、3（TINYINT UNSIGNED）
     */
    private Integer code;

    /**
     * 班次名称：早班、中班、晚班
     */
    private String name;

    /**
     * 开始时间：HH:mm:ss
     */
    private String startTime;

    /**
     * 结束时间：HH:mm:ss
     */
    private String endTime;

    /**
     * 持续时长（小时）
     */
    private Integer durationHours;

    /**
     * 是否跨天
     */
    private Boolean crossDay;
}

