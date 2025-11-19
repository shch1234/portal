package com.weili.iot_portal.business.device_base.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 设备基础信息视图对象
 * <p>
 * 该对象对应 TB 中 org.thingsboard.business.device.entity.DeviceBaseInfo，
 * 但字段类型已按 Portal 规范（字符串存储 UUID）进行适配。
 */
@Data
@Accessors(chain = true)
public class DeviceBaseInfoVO {

    private String id;

    private String tenantId;

    private String tbDeviceId;

    private String deviceCode;

    private String deviceName;

    private String deviceTypeId;

    private String deviceModelId;

    private String deviceTypeName;

    private String deviceSubTypeName;

    private String modelName;

    private String manufacturer;

    /**
     * 数控系统（从 device_model.type_specific_attrs 中获取）
     */
    private String cncSystem;

    /**
     * 控制器型号（从 device_model.type_specific_attrs 中获取）
     */
    private String controllerModel;

    /**
     * IP地址（从 device_configuration 中获取）
     */
    private String ipAddress;

    /**
     * Mac地址（从 device_configuration 中获取）
     */
    private String macAddress;

    private String factoryId;

    private String workshopId;

    private String productionLineId;

    private String factoryName;

    private String workshopName;

    private String productionLineName;

    private String deviceStatus;

    private Boolean isMonitored;

    private Map<String, Object> extraProperties;

    private String remarks;

    private Long createdTime;

    private Long updatedTime;

    private String createdBy;

    private String updatedBy;
}

