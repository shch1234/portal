package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

/**
 * 设备参数历史查询条件
 */
@Data
public class DeviceParameterHistoryQuery {

    private String deviceId;

    private Long startTs;

    private Long endTs;
}


