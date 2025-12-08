package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设备类型分页查询请求
 */
@Data
public class DeviceTypeQueryReq {

    private String typeCodeLike;

    private String typeDictValueLike;

    private String parentTypeId;

    private Integer levelNo;

    private List<String> categories;

    private Boolean isActive;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNo;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 200, message = "每页条数不能超过200")
    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

