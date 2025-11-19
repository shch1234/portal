package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 设备类型视图对象
 */
@Data
@Accessors(chain = true)
public class DeviceTypeVO {

    private String id;

    private String typeCode;

    private String typeName;

    private String parentTypeId;

    private String parentTypeName;

    private Integer level;

    private String category;

    private String description;

    private String icon;

    private Map<String, Object> customFields;

    private Boolean isActive;

    private Integer sortOrder;

    private Long createdTime;

    private Long updatedTime;
}


