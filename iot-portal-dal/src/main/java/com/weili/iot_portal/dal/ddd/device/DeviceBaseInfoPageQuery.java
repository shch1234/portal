package com.weili.iot_portal.dal.ddd.device;

import lombok.Data;

import java.util.List;

/**
 * 设备信息分页查询条件（对应 device_info 表）
 */
@Data
public class DeviceBaseInfoPageQuery {

    /**
     * 租户UUID（已删除，设备信息不再按租户隔离）
     * 如需按租户过滤，需要通过 tb_device_id 查询 ThingsBoard 获取租户信息
     */
    @Deprecated
    private String tenantUuid;

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
     * 注意：此字段在 Repository 层不处理，由 Service 层处理
     */
    private Boolean hasAlarm;

    private Integer pageNo;

    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}

