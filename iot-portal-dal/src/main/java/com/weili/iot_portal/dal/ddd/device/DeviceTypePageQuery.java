package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 设备类型分页查询条件
 */
@Data
public class DeviceTypePageQuery {

    private String tenantId;

    private String typeCodeLike;

    private String typeNameLike;

    private String parentTypeId;

    private Integer level;

    private List<String> categories;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

