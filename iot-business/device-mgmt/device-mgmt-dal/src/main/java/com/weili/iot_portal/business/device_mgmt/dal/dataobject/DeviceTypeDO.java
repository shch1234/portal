package com.weili.iot_portal.business.device_mgmt.dal.dataobject;

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
 * 设备类型数据对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "biz_device_base.device_type", autoResultMap = true)
public class DeviceTypeDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -8564837162533013560L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String typeCode;

    private String typeName;

    private String parentTypeId;

    private Integer level;

    private String category;

    private String description;

    private String icon;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customFields;

    private Boolean isActive;

    private Integer sortOrder;

    private String createdBy;

    private String updatedBy;
}


