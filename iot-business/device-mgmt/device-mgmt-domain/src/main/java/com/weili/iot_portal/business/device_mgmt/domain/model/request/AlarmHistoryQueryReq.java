package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 报警历史查询请求
 */
@Data
public class AlarmHistoryQueryReq {

    private String deviceId;

    private Long startTs;

    private Long endTs;

    private Boolean inProgress;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNo;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 200, message = "每页条数不能超过200")
    private Integer pageSize;
}


