package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterUpdateReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceParameterService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备参数控制器
 */
@Tag(name = "设备管理-设备参数")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/parameters")
@RequiredArgsConstructor
public class DeviceParameterController {

    private final DeviceParameterService deviceParameterService;

    @GetMapping
    @ApiInterceptor
    @Operation(summary = "获取当前设备参数")
    public CommonResult<DeviceParameterVO> getParameters(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceParameterService.getCurrent(tenantId, factoryId, deviceId));
    }

    @PutMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:param:update')")
    @Operation(summary = "更新设备参数")
    public CommonResult<DeviceParameterVO> updateParameters(@PathVariable String deviceId,
                                                            @Valid @RequestBody DeviceParameterUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        String operator = SecurityFrameworkContext.getLoginUserName();
        return CommonResult.success(deviceParameterService.updateParameters(tenantId, factoryId, deviceId, operator, request));
    }

    @PostMapping("/history")
    @ApiInterceptor
    @Operation(summary = "查询设备参数历史")
    public CommonResult<DeviceParameterHistoryVO> history(@PathVariable String deviceId,
                                                          @Valid @RequestBody DeviceParameterHistoryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(deviceId);
        return CommonResult.success(deviceParameterService.getHistory(tenantId, factoryId, request));
    }
}


