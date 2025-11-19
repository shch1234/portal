package com.weili.iot_portal.business.device_mgmt.dal.ddd;

import lombok.Data;

import java.util.List;

/**
 * 设备基础信息分页查询条件
 */
@Data
public class DeviceBaseInfoPageQuery {

    private String tenantId;

    private String deviceCodeLike;

    private String deviceNameLike;

    private List<String> deviceTypeIds;

    /**
     * 设备子类型名称列表
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
     * 注意：此字段在 Repository 层不处理，由 Service 层处理
     */
    private Boolean hasAlarm;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}


