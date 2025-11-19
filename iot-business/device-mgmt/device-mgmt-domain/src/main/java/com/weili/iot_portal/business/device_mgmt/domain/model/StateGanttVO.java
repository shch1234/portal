package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 状态甘特响应
 */
@Data
public class StateGanttVO {

    private List<StateGanttSegmentVO> segments;

    private Long total;

    private Long startTs;

    private Long endTs;
}


