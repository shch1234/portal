package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

/**
 * 设备配置分页查询
 */
@Data
public class DeviceConfigurationPageQuery {

    private String tenantId;

    private String factoryId;

    private String ipAddress;

    private String protocol;

    private String locationCode;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

