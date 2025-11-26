package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

@Data
public class ToolUsageItemVO {

    private String toolNumber;

    private String toolHolderNumber;

    private Long startTs;

    private Long endTs;

    private Long durationMs;
}

