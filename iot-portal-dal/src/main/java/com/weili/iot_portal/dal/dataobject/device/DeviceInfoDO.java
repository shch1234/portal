package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

/**
 * 设备信息数据对象（对应 device_info 表）
 * 关联 ThingsBoard 设备实例，包含设备的基础信息和组织结构信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_info", autoResultMap = true)
public class DeviceInfoDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 3545101187655639470L;

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 租户UUID（对应 tenant_uuid 列）
     */
    private String tenantUuid;

    /**
     * ThingsBoard 设备ID（关联 ThingsBoard device.id，对应 tb_device_id 列）
     */
    private String tbDeviceId;

    /**
     * 设备编号（威力编号，租户内唯一，对应 device_code 列）
     */
    private String deviceCode;

    /**
     * 设备名称（对应 device_name 列）
     */
    private String deviceName;

    /**
     * 设备类型编码（关联 device_type_relation.type_code，对应 device_type_code 列）
     */
    private String deviceTypeCode;

    /**
     * 设备型号ID（关联 device_model.id，对应 device_model_id 列）
     */
    private String deviceModelId;

    /**
     * 设备类型名称（冗余字段，优化查询，对应 device_type_name 列）
     */
    private String deviceTypeName;

    /**
     * 设备子类型名称（冗余字段，优化查询，对应 device_sub_type_name 列）
     */
    private String deviceSubTypeName;

    /**
     * 型号名称（冗余字段，优化查询，对应 model_name 列）
     */
    private String modelName;

    /**
     * 制造商（冗余字段，优化查询，对应 manufacturer 列）
     */
    private String manufacturer;

    /**
     * 所属厂区ID（关联 device_org_relation.id，对应 org_factory_id 列）
     */
    private String orgFactoryId;

    /**
     * 所属车间ID（关联 device_org_relation.id，对应 org_workshop_id 列）
     */
    private String orgWorkshopId;

    /**
     * 所属产线ID（关联 device_org_relation.id，对应 org_production_line_id 列）
     */
    private String orgProductionLineId;

    /**
     * 厂区名称（冗余字段，优化查询，对应 factory_name 列）
     */
    private String factoryName;

    /**
     * 车间名称（冗余字段，优化查询，对应 workshop_name 列）
     */
    private String workshopName;

    /**
     * 产线名称（冗余字段，优化查询，对应 production_line_name 列）
     */
    private String productionLineName;

    /**
     * 设备状态：ACTIVE-在用 INACTIVE-停用 MAINTENANCE-维护中 RETIRED-报废（对应 device_status 列）
     */
    private String deviceStatus;

    /**
     * 是否监控：1-监控 0-不监控（对应 is_monitored 列）
     */
    private Boolean isMonitored;

    /**
     * 扩展属性（JSON）：采购信息、资产编号、序列号等（对应 extra_properties 列）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraProperties;

    /**
     * 备注信息（对应 remarks 列）
     */
    private String remarks;
}

