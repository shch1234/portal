package com.weili.iot_portal.business.device_mgmt.domain.model;

import com.weili.iot_portal.business.device_mgmt.domain.enums.DeviceStateEnum;
import lombok.Data;

/**
 * 状态统计项
 */
@Data
public class StateStatsItemVO {

    private DeviceStateEnum state;

    private Long durationMs;

    private Double ratio;
}


