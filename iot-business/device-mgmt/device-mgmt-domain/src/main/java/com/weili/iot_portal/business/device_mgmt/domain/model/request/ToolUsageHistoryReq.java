package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ToolUsageHistoryReq {

    @NotBlank
    private String deviceId;

    @NotNull
    private Long startTs;

    @NotNull
    private Long endTs;

    private Integer limit;
}

