package com.weili.iot_portal.dal.dataobject.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 组织单元数据对象（对应 device_org_relation 表）
 * 用于描述设备所在的组织单元，如厂区、车间、产线
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_org_relation")
public class DeviceOrgRelationDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = -7263912751231102451L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 组织单元编码（全局唯一，对应 unit_code 列）
     */
    private String unitCode;

    /**
     * 组织单元名称（对应 unit_name 列）
     */
    private String unitName;

    /**
     * 组织单元类型值（system_dict_data.value，FACTORY/WORKSHOP/PRODUCTION_LINE，对应 unit_type_value 列）
     */
    private String unitTypeValue;

    /**
     * 父级组织ID（关联 device_org_relation.id，对应 org_parent_id 列）
     */
    private String orgParentId;

    /**
     * 层级：1厂区、2车间、3产线（对应 level_no 列）
     */
    private Integer levelNo;

    /**
     * 层级路径（物化路径模式）：使用 unit_code 组合，以 / 分隔（对应 path 列）
     */
    private String path;

    /**
     * 描述信息（对应 description 列）
     */
    private String description;

    /**
     * 是否启用（对应 is_active 列）
     */
    private Boolean isActive;
}

