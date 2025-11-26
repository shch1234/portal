package com.weili.iot_portal.domain.devicemng.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 产量历史查询请求
 */
@Data
public class ProductionHistoryReq {

    private String deviceId;

    private Long startTs;

    private Long endTs;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNo;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 500, message = "每页条数不能超过500")
    private Integer pageSize;
}


