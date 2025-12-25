package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 设备型号分页查询
 */
@Data
public class DeviceModelPageQuery {

    private String modelCode;

    private String modelName;

    private List<String> deviceTypeCodes;

    private String manufacturer;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

