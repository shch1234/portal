package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;

@Data
public class ToolCompensationVO {

    private String deviceId;

    private List<ToolCompensationSlotVO> lengthSlots;

    private List<ToolCompensationSlotVO> radiusSlots;

    private Long lastUpdatedTs;
}

