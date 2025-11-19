package com.weili.iot_portal.business.device_base.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceBaseInfoUpdateReq;
import com.weili.iot_portal.business.device_base.service.DeviceBaseInfoService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备基础信息管理（主数据模块）
 */
@Tag(name = "设备基础数据-设备基础信息")
@RestController
@RequestMapping("/api/device-base/v1/device-info")
@RequiredArgsConstructor
public class DeviceBaseInfoController {

    private final DeviceBaseInfoService deviceBaseInfoService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device-base:info:create')")
    @Operation(summary = "创建设备基础信息")
    public CommonResult<DeviceBaseInfoVO> create(@Valid @RequestBody DeviceBaseInfoCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        return CommonResult.success(deviceBaseInfoService.create(tenantId, operator, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:info:update')")
    @Operation(summary = "更新设备基础信息")
    public CommonResult<DeviceBaseInfoVO> update(@PathVariable("id") String id,
                                                 @Valid @RequestBody DeviceBaseInfoUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        request.setId(id);
        return CommonResult.success(deviceBaseInfoService.update(tenantId, operator, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询设备基础信息")
    public CommonResult<DeviceBaseInfoVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceBaseInfoService.getById(tenantId, factoryId, id));
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询设备基础信息")
    public CommonResult<PageResult<DeviceBaseInfoVO>> page(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceBaseInfoService.page(tenantId, factoryId, request));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询设备列表（含列表展示字段）")
    public CommonResult<PageResult<DeviceBaseInfoListVO>> list(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceBaseInfoService.list(tenantId, factoryId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device-base:info:delete')")
    @Operation(summary = "删除设备基础信息")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceBaseInfoService.delete(tenantId, id));
    }
}

