package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 公共的设备信息请求字段（对应 device_info 表）
 */
@Data
public class DeviceBaseInfoBaseReq {

    /**
     * 设备编号（对应 device_code）
     */
    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    /**
     * 设备名称（对应 device_name）
     */
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    /**
     * 设备类型编码（对应 device_type_code）
     */
    @NotBlank(message = "设备类型不能为空")
    private String deviceTypeCode;

    /**
     * 设备型号ID（对应 device_model_id）
     */
    @NotBlank(message = "设备型号不能为空")
    private String deviceModelId;

    /**
     * TB 设备 ID（对应 tb_device_id），可在关联时进行填充
     */
    private String tbDeviceId;

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
     * 备注信息（对应 remarks）
     */
    private String remarks;

    /**
     * 扩展属性（对应 extra_properties）
     */
    private Map<String, Object> extraProperties;
}

