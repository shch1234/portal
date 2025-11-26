package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 设备关系分页查询
 */
@Data
public class DeviceRelationPageQuery {

    private String tenantId;

    private String fromDeviceId;

    private String toDeviceId;

    private List<String> relationTypes;

    private Boolean isActive;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

