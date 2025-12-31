package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

/**
 * 设备类型分页查询条件
 */
@Data
public class DeviceTypePageQuery {


    private String typeCode;

    private String parentTypeId;

    private Integer levelNo;

    private String category;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;

    private String description;
}

