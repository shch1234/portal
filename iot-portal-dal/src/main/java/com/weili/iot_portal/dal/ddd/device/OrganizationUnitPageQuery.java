package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 组织单元分页查询条件
 */
@Data
public class OrganizationUnitPageQuery {

    private String tenantId;

    private String unitCodeLike;

    private String unitNameLike;

    private List<String> unitTypes;

    private String parentId;

    private Integer level;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

