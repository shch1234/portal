package com.weili.iot_portal.dal.dataobject.devicemng;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weili.basic.framework.mybatis.domain.BaseSimpleDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 组织单元数据对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "device_organization_unit")
public class OrganizationUnitDO extends BaseSimpleDO {

    @Serial
    private static final long serialVersionUID = -7263912751231102451L;

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;

    private String unitCode;

    private String unitName;

    private String unitType;

    private String parentId;

    private Integer level;

    private String path;

    private String description;

    private String location;

    private Boolean isActive;

    private Integer sortOrder;

    private String createdBy;

    private String updatedBy;
}


