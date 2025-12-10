package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.OrganizationUnitVO;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitUpdateReq;
import com.weili.iot_portal.service.devicebase.OrganizationUnitService;
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
@RequestMapping("/device-base/organization-units")
@RequiredArgsConstructor
public class OrganizationUnitController {

    private final OrganizationUnitService organizationUnitService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device-base:org:create')")
    @ApiInterceptor
    @Operation(summary = "创建组织单元")
    public CommonResult<OrganizationUnitVO> create(@Valid @RequestBody OrganizationUnitCreateReq request) {
        return CommonResult.success(organizationUnitService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:org:update')")
    @ApiInterceptor
    @Operation(summary = "更新组织单元")
    public CommonResult<OrganizationUnitVO> update(@PathVariable("id") String id,
                                                   @Valid @RequestBody OrganizationUnitUpdateReq request) {
        request.setId(id);
        return CommonResult.success(organizationUnitService.update(request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询组织单元详情")
    public CommonResult<OrganizationUnitVO> get(@PathVariable("id") String id) {
        return CommonResult.success(organizationUnitService.get(id));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询组织单元")
    public CommonResult<PageResult<OrganizationUnitVO>> page(@Valid @RequestBody OrganizationUnitQueryReq request) {
        return CommonResult.success(organizationUnitService.page(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:org:delete')")
    @ApiInterceptor
    @Operation(summary = "删除组织单元")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        return CommonResult.success(organizationUnitService.delete(id));
    }
}

