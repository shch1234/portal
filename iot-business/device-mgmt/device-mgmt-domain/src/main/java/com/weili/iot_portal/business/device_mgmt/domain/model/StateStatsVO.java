package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 状态统计响应
 */
@Data
public class StateStatsVO {

    private String deviceId;

    private Long startTs;

    private Long endTs;

    private Long totalDuration;

    private List<StateStatsItemVO> stats;
}


