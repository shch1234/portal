package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 设备基础信息列表视图对象
 * <p>
 * 用于设备列表展示，包含列表所需的所有字段
 */
@Data
@Accessors(chain = true)
public class DeviceBaseInfoListVO {

    /**
     * 设备ID
     */
    private String id;

    /**
     * 设备编号（威力编号）
     */
    private String deviceCode;

    /**
     * 设备类型名称
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称
     */
    private String deviceSubTypeName;

    /**
     * 规格型号
     */
    private String modelName;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 当前运行状态（实时状态：加工中/待机/故障/关机）
     * 从 ThingsBoard 实时数据获取
     */
    private String currentStatus;

    /**
     * 是否报警中
     */
    private Boolean hasAlarm;
}

