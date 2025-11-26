package com.weili.iot_portal.domain.devicemng;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
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


