package com.weili.iot_portal.business.common.domain.model.device;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 设备基础信息VO（公共领域模型）
 * 
 * <p>用于各业务模块之间传递设备基础信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceBaseInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private String id;

    /**
     * ThingsBoard设备ID
     */
    private String tbDeviceId;

    /**
     * 设备编号（威力编号）
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备类型ID
     */
    private String deviceTypeId;

    /**
     * 设备类型名称
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称
     */
    private String deviceSubTypeName;

    /**
     * 设备型号ID
     */
    private String deviceModelId;

    /**
     * 型号名称
     */
    private String modelName;

    /**
     * 制造商
     */
    private String manufacturer;

    /**
     * 所属厂区ID
     */
    private String factoryId;

    /**
     * 厂区名称
     */
    private String factoryName;

    /**
     * 所属车间ID
     */
    private String workshopId;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 所属产线ID
     */
    private String productionLineId;

    /**
     * 产线名称
     */
    private String productionLineName;

    /**
     * 设备状态
     */
    private String deviceStatus;

    /**
     * 是否监控
     */
    private Boolean isMonitored;

    /**
     * IP 地址
     */
    private String ipAddress;

    /**
     * Mac 地址
     */
    private String macAddress;
}

