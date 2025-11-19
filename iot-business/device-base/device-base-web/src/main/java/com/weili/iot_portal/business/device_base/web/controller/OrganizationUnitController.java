package com.weili.iot_portal.business.device_base.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_base.domain.model.OrganizationUnitVO;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.OrganizationUnitUpdateReq;
import com.weili.iot_portal.business.device_base.service.OrganizationUnitService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 组织单元管理（主数据模块）
 */
@Tag(name = "设备基础数据-组织单元")
@RestController
@RequestMapping("/api/device-base/v1/organization-units")
@RequiredArgsConstructor
public class OrganizationUnitController {

    private final OrganizationUnitService organizationUnitService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device-base:org:create')")
    @ApiInterceptor
    @Operation(summary = "创建组织单元")
    public CommonResult<OrganizationUnitVO> create(@Valid @RequestBody OrganizationUnitCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        return CommonResult.success(organizationUnitService.create(tenantId, operator, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:org:update')")
    @ApiInterceptor
    @Operation(summary = "更新组织单元")
    public CommonResult<OrganizationUnitVO> update(@PathVariable("id") String id,
                                                   @Valid @RequestBody OrganizationUnitUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        request.setId(id);
        return CommonResult.success(organizationUnitService.update(tenantId, operator, request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询组织单元详情")
    public CommonResult<OrganizationUnitVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(organizationUnitService.get(tenantId, id));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询组织单元")
    public CommonResult<PageResult<OrganizationUnitVO>> page(@Valid @RequestBody OrganizationUnitQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(organizationUnitService.page(tenantId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:org:delete')")
    @ApiInterceptor
    @Operation(summary = "删除组织单元")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(organizationUnitService.delete(tenantId, id));
    }
}

