package com.weili.iot_portal.business.device_base.domain.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设备基础信息查询请求
 */
@Data
public class DeviceBaseInfoQueryReq {

    private String deviceCodeLike;

    private String deviceNameLike;

    private List<String> deviceTypeIds;

    /**
     * 设备子类型名称列表（用于筛选）
     */
    private List<String> deviceSubTypeNames;

    private List<String> deviceModelIds;

    private List<String> factoryIds;

    private List<String> workshopIds;

    private List<String> productionLineIds;

    private List<String> deviceStatuses;

    private Boolean isMonitored;

    /**
     * 是否报警中（true=仅查询有报警的设备，false=仅查询无报警的设备，null=不筛选）
     */
    private Boolean hasAlarm;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于 1")
    private Integer pageNo;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

