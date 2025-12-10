package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 设备网络配置分页查询
 */
@Data
public class DeviceNetworkConfigPageQuery {

    private String factoryId;

    /**
     * 基于位置筛选得到的设备ID列表
     */
    private List<String> deviceIds;

    private String ipAddress;

    private String protocol;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

