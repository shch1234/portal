package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ToolCompensationVO {
    private Long id;
    private String factoryId;
    private String deviceId;
    private String toolHolderNo;
    private Integer version;
    private Long startTs;
    private Long endTs;
    private Map<String, Object> compValue;
    
    // 实时缓存字段（用于 ToolCompensationCache）
    private List<ToolCompensationSlotVO> lengthSlots;
    private List<ToolCompensationSlotVO> radiusSlots;
    private Long lastUpdatedTs;
}
