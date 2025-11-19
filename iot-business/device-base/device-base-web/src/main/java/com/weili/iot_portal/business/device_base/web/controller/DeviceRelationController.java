package com.weili.iot_portal.business.device_base.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_base.domain.model.DeviceRelationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationUpdateReq;
import com.weili.iot_portal.business.device_base.service.DeviceRelationService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备关系控制器
 */
@Tag(name = "设备基础数据-设备关系")
@RestController
@RequestMapping("/api/device-base/v1/device-relations")
@RequiredArgsConstructor
public class DeviceRelationController {

    private final DeviceRelationService deviceRelationService;

    @PostMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:create')")
    @Operation(summary = "创建设备关系")
    public CommonResult<DeviceRelationVO> create(@Valid @RequestBody DeviceRelationCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        return CommonResult.success(deviceRelationService.create(tenantId, operator, request));
    }

    @PutMapping("/{id}")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:update')")
    @Operation(summary = "更新设备关系")
    public CommonResult<DeviceRelationVO> update(@PathVariable String id,
                                                 @Valid @RequestBody DeviceRelationUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        request.setId(id);
        return CommonResult.success(deviceRelationService.update(tenantId, operator, request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "查询设备关系详情")
    public CommonResult<DeviceRelationVO> get(@PathVariable String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.get(tenantId, id));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备关系")
    public CommonResult<PageResult<DeviceRelationVO>> page(@Valid @RequestBody DeviceRelationQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.page(tenantId, request));
    }

    @DeleteMapping("/{id}")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:relation:delete')")
    @Operation(summary = "删除设备关系")
    public CommonResult<Boolean> delete(@PathVariable String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceRelationService.delete(tenantId, id));
    }
}

