package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 组织单元分页查询条件（对应 device_org_relation 表）
 */
@Data
public class DeviceOrgRelationPageQuery {

    /**
     * 单元编码模糊查询（对应 unit_code）
     */
    private String unitCodeLike;

    /**
     * 单元名称模糊查询（对应 unit_name）
     */
    private String unitNameLike;

    /**
     * 单元类型值列表（对应 unit_type_value）
     */
    private List<String> unitTypeValues;

    /**
     * 父级组织ID（对应 org_parent_id）
     */
    private String orgParentId;

    /**
     * 层级（对应 level_no）
     */
    private Integer levelNo;

    /**
     * 是否启用（对应 is_active）
     */
    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

