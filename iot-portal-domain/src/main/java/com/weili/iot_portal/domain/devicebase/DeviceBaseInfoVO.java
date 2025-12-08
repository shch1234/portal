package com.weili.iot_portal.domain.devicebase;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备信息视图对象（对应 device_info 表）
 * <p>
 * 字段类型已按 Portal 规范（字符串存储 UUID）进行适配。
 */
@Data
@Accessors(chain = true)
@Builder
public class DeviceBaseInfoVO {

    private String id;

    /**
     * 租户UUID（对应 tenant_uuid）
     */
    private String tenantUuid;

    /**
     * ThingsBoard 设备ID（对应 tb_device_id）
     */
    private String tbDeviceId;

    /**
     * 设备编号（对应 device_code）
     */
    private String deviceCode;

    /**
     * 设备名称（对应 device_name）
     */
    private String deviceName;

    /**
     * 设备类型编码（对应 device_type_code）
     */
    private String deviceTypeCode;

    /**
     * 设备型号ID（对应 device_model_id）
     */
    private String deviceModelId;

    /**
     * 设备类型名称（冗余字段，对应 device_type_name）
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称（冗余字段，对应 device_sub_type_name）
     */
    private String deviceSubTypeName;

    /**
     * 型号名称（冗余字段，对应 model_name）
     */
    private String modelName;

    /**
     * 制造商（冗余字段，对应 manufacturer）
     */
    private String manufacturer;

    /**
     * 数控系统（从 device_model.type_specific_attrs 中获取）
     */
    private String cncSystem;

    /**
     * 控制器型号（从 device_model.type_specific_attrs 中获取）
     */
    private String controllerModel;

    /**
     * IP地址（从 device_network_config 中获取）
     */
    private String ipAddress;

    /**
     * Mac地址（从 device_network_config 中获取）
     */
    private String macAddress;

    /**
     * 所属厂区ID（对应 org_factory_id）
     */
    private String orgFactoryId;

    /**
     * 所属车间ID（对应 org_workshop_id）
     */
    private String orgWorkshopId;

    /**
     * 所属产线ID（对应 org_production_line_id）
     */
    private String orgProductionLineId;

    /**
     * 厂区名称（冗余字段，对应 factory_name）
     */
    private String factoryName;

    /**
     * 车间名称（冗余字段，对应 workshop_name）
     */
    private String workshopName;

    /**
     * 产线名称（冗余字段，对应 production_line_name）
     */
    private String productionLineName;

    /**
     * 设备状态（对应 device_status）
     */
    private String deviceStatus;

    /**
     * 是否监控（对应 is_monitored）
     */
    private Boolean isMonitored;

    /**
     * 扩展属性（对应 extra_properties）
     */
    private Map<String, Object> extraProperties;

    /**
     * 备注信息（对应 remarks）
     */
    private String remarks;

    private LocalDateTime createdTime;

    private LocalDateTime updateTime;

    private String createdBy;

    private String updatedBy;
}

