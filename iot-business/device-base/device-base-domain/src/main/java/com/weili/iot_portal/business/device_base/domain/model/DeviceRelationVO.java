package com.weili.iot_portal.business.device_base.domain.model;

import lombok.Data;

import java.util.Map;

/**
 * 设备关系视图对象
 */
@Data
public class DeviceRelationVO {

    private String id;

    private String fromDeviceId;

    private String fromDeviceCode;

    private String fromDeviceName;

    private String toDeviceId;

    private String toDeviceCode;

    private String toDeviceName;

    private String relationType;

    private String relationName;

    private String description;

    private Map<String, Object> properties;

    private Boolean isActive;

    private Long createdTime;

    private Long updatedTime;

    private String createdBy;

    private String updatedBy;
}

