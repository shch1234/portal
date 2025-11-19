package com.weili.iot_portal.business.device_mgmt.dal.ddd;

import lombok.Data;

/**
 * 设备参数历史查询条件
 */
@Data
public class DeviceParameterHistoryQuery {

    private String tenantId;

    private String deviceId;

    private Long startTs;

    private Long endTs;
}


