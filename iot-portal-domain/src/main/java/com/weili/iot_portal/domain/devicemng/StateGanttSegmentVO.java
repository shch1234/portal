package com.weili.iot_portal.domain.devicemng;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import lombok.Data;

/**
 * 状态甘特片段
 */
@Data
public class StateGanttSegmentVO {

    private String id;

    private DeviceStateEnum state;

    private Long startTs;

    private Long endTs;

    private Long durationMs;
}


