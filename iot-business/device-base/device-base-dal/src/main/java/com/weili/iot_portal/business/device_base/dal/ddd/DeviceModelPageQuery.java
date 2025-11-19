package com.weili.iot_portal.business.device_base.dal.ddd;

import lombok.Data;

import java.util.List;

/**
 * 设备型号分页查询
 */
@Data
public class DeviceModelPageQuery {

    private String tenantId;

    private String modelCodeLike;

    private String modelNameLike;

    private List<String> deviceTypeIds;

    private String manufacturer;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

