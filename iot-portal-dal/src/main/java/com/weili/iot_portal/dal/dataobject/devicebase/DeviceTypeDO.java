package com.weili.iot_portal.dal.dataobject.devicebase;

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
@TableName(value = "device_type_relation", autoResultMap = true)
public class DeviceTypeDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -8564837162533013560L;

    @TableId(type = IdType.INPUT)
    private String id;

    /** 租户UUID，对应 tenant_uuid */
    private String tenantUuid;

    private String typeCode;

    /** 类型字典值，对应 type_dict_value */
    private String typeDictValue;

    /** 父类型ID，对应 parent_type_id */
    private String parentTypeId;

    /** 父类型编码，对应 parent_type_code */
    private String parentTypeCode;

    /** 父类型字典值，对应 parent_dict_value */
    private String parentDictValue;

    /** 层级，对应 level_no */
    private Integer levelNo;

    private String category;

    private String description;

    private String icon;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> customFields;

    /** 是否启用，对应 is_active */
    private Boolean isActive;

    /** 排序号，对应 sort_order */
    private Integer sortOrder;
}

