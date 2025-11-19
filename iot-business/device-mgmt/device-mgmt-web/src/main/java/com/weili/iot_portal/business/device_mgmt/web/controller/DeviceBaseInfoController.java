package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoUpdateReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceBaseInfoService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备基础信息控制器
 */
@Tag(name = "设备管理-设备基础信息")
@RestController
@RequestMapping("/api/device-mgmt/v1/device-base-info")
@RequiredArgsConstructor
public class DeviceBaseInfoController {

    private final DeviceBaseInfoService deviceBaseInfoService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device:base:create')")
    @Operation(summary = "创建设备基础信息")
    @ApiInterceptor
    public CommonResult<DeviceBaseInfoVO> create(@Valid @RequestBody DeviceBaseInfoCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        DeviceBaseInfoVO result = deviceBaseInfoService.create(tenantId, operator, request);
        return CommonResult.success(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:base:update')")
    @Operation(summary = "更新设备基础信息")
    @ApiInterceptor
    public CommonResult<DeviceBaseInfoVO> update(@PathVariable("id") String id,
                                                 @Valid @RequestBody DeviceBaseInfoUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        request.setId(id);
        DeviceBaseInfoVO result = deviceBaseInfoService.update(tenantId, operator, request);
        return CommonResult.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询设备基础信息")
    @ApiInterceptor
    public CommonResult<DeviceBaseInfoVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        DeviceBaseInfoVO result = deviceBaseInfoService.getById(tenantId, factoryId, id);
        return CommonResult.success(result);
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询设备基础信息")
    @ApiInterceptor
    public CommonResult<PageResult<DeviceBaseInfoVO>> page(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        PageResult<DeviceBaseInfoVO> result = deviceBaseInfoService.page(tenantId, factoryId, request);
        return CommonResult.success(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:base:delete')")
    @Operation(summary = "删除设备基础信息")
    @ApiInterceptor
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        boolean success = deviceBaseInfoService.delete(tenantId, id);
        return CommonResult.success(success);
    }
}


