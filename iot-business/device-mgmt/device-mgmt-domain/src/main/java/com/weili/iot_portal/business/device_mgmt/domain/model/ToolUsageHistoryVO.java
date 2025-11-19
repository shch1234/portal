package com.weili.iot_portal.business.device_mgmt.domain.model;

import lombok.Data;

import java.util.List;

@Data
public class ToolUsageHistoryVO {

    private String deviceId;

    private Long startTs;

    private Long endTs;

    private List<ToolUsageItemVO> items;
}

