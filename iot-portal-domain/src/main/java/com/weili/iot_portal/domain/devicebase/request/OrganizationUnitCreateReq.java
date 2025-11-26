package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建组织单元请求
 */
@Data
public class OrganizationUnitCreateReq {

    @NotBlank(message = "单元编码不能为空")
    private String unitCode;

    @NotBlank(message = "单元名称不能为空")
    private String unitName;

    @NotBlank(message = "单元类型不能为空")
    private String unitType;

    @NotNull(message = "层级不能为空")
    private Integer level;

    /**
     * 父级组织单元ID，可为空
     */
    private String parentId;

    private String description;

    private String location;

    private Boolean isActive;

    private Integer sortOrder;
}

