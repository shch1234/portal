package com.weili.iot_portal.domain.devicebase;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 组织单元视图对象（对应 device_org_relation 表）
 */
@Data
@Accessors(chain = true)
public class OrganizationUnitVO {

    private String id;

    /**
     * 组织单元编码
     */
    private String unitCode;

    /**
     * 组织单元名称
     */
    private String unitName;

    /**
     * 组织单元类型值（FACTORY/WORKSHOP/PRODUCTION_LINE）
     */
    private String unitTypeValue;

    /**
     * 父级组织ID
     */
    private String orgParentId;

    /**
     * 父级组织名称（冗余字段，用于展示）
     */
    private String parentName;

    /**
     * 层级：1厂区、2车间、3产线
     */
    private Integer levelNo;

    /**
     * 层级路径（物化路径模式）
     */
    private String path;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 排序号
     */
    private Integer sortOrder;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

