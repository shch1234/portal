package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 设备信息查询请求（对应 device_info 表）
 */
@Data
public class DeviceBaseInfoQueryReq {

    /**
     * 厂区ID（对应 org_factory_id）
     */
    private String orgFactoryId;

    /**
     * 设备编号模糊查询（对应 device_code）
     */
    private String deviceCodeLike;

    /**
     * 设备名称模糊查询（对应 device_name）
     */
    private String deviceNameLike;

    /**
     * 设备类型编码列表（对应 device_type_code）
     */
    private List<String> deviceTypeCodes;

    /**
     * 设备子类型名称列表（对应 device_sub_type_name，冗余字段）
     */
    private List<String> deviceSubTypeNames;

    /**
     * 设备型号ID列表（对应 device_model_id）
     */
    private List<String> deviceModelIds;

    /**
     * 厂区ID列表（对应 org_factory_id）
     */
    private List<String> orgFactoryIds;

    /**
     * 车间ID列表（对应 org_workshop_id）
     */
    private List<String> orgWorkshopIds;

    /**
     * 产线ID列表（对应 org_production_line_id）
     */
    private List<String> orgProductionLineIds;

    /**
     * 设备状态列表（对应 device_status）
     */
    private List<String> deviceStatuses;

    /**
     * 是否监控（对应 is_monitored）
     */
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

