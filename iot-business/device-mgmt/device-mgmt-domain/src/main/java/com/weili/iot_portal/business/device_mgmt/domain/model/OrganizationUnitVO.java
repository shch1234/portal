package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 组织单元视图对象
 */
@Data
@Accessors(chain = true)
public class OrganizationUnitVO {

    private String id;

    private String unitCode;

    private String unitName;

    private String unitType;

    private String parentId;

    private String parentName;

    private Integer level;

    private String path;

    private String description;

    private String location;

    private Boolean isActive;

    private Integer sortOrder;

    private Long createdTime;

    private Long updatedTime;

    private String createdBy;

    private String updatedBy;
}


