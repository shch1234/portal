package com.weili.iot_portal.business.device_mgmt.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新组织单元请求
 */
@Data
public class OrganizationUnitUpdateReq {

    @NotBlank(message = "组织单元ID不能为空")
    private String id;

    private String unitName;

    private String description;

    private String location;

    private Boolean isActive;

    private Integer sortOrder;
}


