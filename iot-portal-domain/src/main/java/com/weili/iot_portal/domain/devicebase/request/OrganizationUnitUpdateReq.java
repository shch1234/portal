package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新组织单元请求（对应 device_org_relation 表）
 */
@Data
public class OrganizationUnitUpdateReq {

    @NotBlank(message = "组织单元ID不能为空")
    private String id;

    /**
     * 组织单元名称
     */
    private String unitName;

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
