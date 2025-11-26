package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

/**
 * 状态时间线查询条件
 */
@Data
public class DeviceStateTimelineQuery {

    private String tenantId;

    private String deviceId;

    private Long startTs;

    private Long endTs;

    private Integer limit;
}


