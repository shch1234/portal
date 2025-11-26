package com.weili.iot_portal.domain.devicebase;

import lombok.Data;

import java.time.LocalDateTime;
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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String createdBy;

    private String updatedBy;
}

