package com.weili.iot_portal.business.device_mgmt.dal.ddd;

import lombok.Data;

/**
 * 产量统计分页查询
 */
@Data
public class ProductionCounterPageQuery {

    private String tenantId;

    private String deviceId;

    private Long startTs;

    private Long endTs;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}


