package com.weili.iot_portal.domain.devicebase;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备型号视图对象
 */
@Data
@Accessors(chain = true)
public class DeviceModelVO {

    private String id;

    private String modelCode;

    private String modelName;

    private String deviceTypeId;

    private String deviceTypeName;

    private String manufacturer;

    private Map<String, Object> specifications;

    private Map<String, Object> typeSpecificAttrs;

    private Boolean isActive;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

