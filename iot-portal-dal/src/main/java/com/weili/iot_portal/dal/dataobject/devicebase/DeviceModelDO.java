package com.weili.iot_portal.dal.dataobject.devicebase;

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
 * 设备型号数据对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_model", autoResultMap = true)
public class DeviceModelDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = -4567529347749408968L;

    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 租户UUID，对应 tenant_uuid
     */
    private String tenantUuid;

    private String modelCode;

    private String modelName;

    /**
     * 设备类型编码，对应 device_type_code
     */
    private String deviceTypeCode;

    private String manufacturer;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> specifications;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> typeSpecificAttrs;

    /**
     * 是否启用，对应 is_active
     */
    private Boolean isActive;

}

