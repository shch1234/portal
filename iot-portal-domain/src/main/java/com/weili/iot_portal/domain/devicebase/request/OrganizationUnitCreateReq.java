package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建组织单元请求（对应 device_org_relation 表）
 */
@Data
public class OrganizationUnitCreateReq {

    @NotBlank(message = "单元编码不能为空")
    private String unitCode;

    @NotBlank(message = "单元名称不能为空")
    private String unitName;

    /**
     * 组织单元类型值（system_dict_data.value，FACTORY/WORKSHOP/PRODUCTION_LINE）
     */
    @NotBlank(message = "单元类型值不能为空")
    private String unitTypeValue;

    /**
     * 层级：1厂区、2车间、3产线
     */
    @NotNull(message = "层级不能为空")
    private Integer levelNo;

    /**
     * 父级组织单元ID（关联 device_org_relation.id），可为空
     */
    private String orgParentId;

    /**
     * 层级路径（物化路径模式），可选，系统可自动生成
     */
    private String path;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 排序号
     */
    private Integer sortOrder;
}

