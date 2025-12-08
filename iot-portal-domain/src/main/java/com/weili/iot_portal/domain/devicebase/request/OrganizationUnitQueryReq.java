package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 组织单元分页查询请求（对应 device_org_relation 表）
 */
@Data
public class OrganizationUnitQueryReq {

    /**
     * 单元编码模糊查询
     */
    private String unitCodeLike;

    /**
     * 单元名称模糊查询
     */
    private String unitNameLike;

    /**
     * 单元类型值列表（FACTORY/WORKSHOP/PRODUCTION_LINE）
     */
    private List<String> unitTypeValues;

    /**
     * 父级组织ID
     */
    private String orgParentId;

    /**
     * 层级：1厂区、2车间、3产线
     */
    private Integer levelNo;

    /**
     * 是否启用
     */
    private Boolean isActive;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNo;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数必须大于等于1")
    @Max(value = 200, message = "每页条数不能超过200")
    private Integer pageSize;

    private String sortBy;

    private String sortDirection;
}
