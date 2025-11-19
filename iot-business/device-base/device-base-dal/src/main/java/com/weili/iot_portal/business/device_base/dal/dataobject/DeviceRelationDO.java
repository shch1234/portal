package com.weili.iot_portal.business.device_base.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Map;

/**
 * 设备关系 DO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_device_base.device_relation", autoResultMap = true)
public class DeviceRelationDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = 5313832692281889747L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String fromDeviceId;

    private String toDeviceId;

    private String relationType;

    private String relationName;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;

    private Boolean isActive;

    private String createdBy;

    private String updatedBy;
}

