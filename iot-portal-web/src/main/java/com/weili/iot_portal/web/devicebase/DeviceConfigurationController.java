package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceConfigurationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备配置管理
 */
@Tag(name = "设备基础数据-设备配置")
@RestController
@RequestMapping("/device-base/device-configurations")
@RequiredArgsConstructor
public class DeviceConfigurationController {

    private final DeviceConfigurationService deviceConfigurationService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device:config:create')")
    @ApiInterceptor
    @Operation(summary = "创建设备配置")
    public CommonResult<DeviceConfigurationVO> create(@Valid @RequestBody DeviceConfigurationCreateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceConfigurationService.create(tenantId,request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:config:update')")
    @ApiInterceptor
    @Operation(summary = "更新设备配置")
    public CommonResult<DeviceConfigurationVO> update(@PathVariable("id") String id,
                                                      @Valid @RequestBody DeviceConfigurationUpdateReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        request.setId(id);
        return CommonResult.success(deviceConfigurationService.update(tenantId, request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "根据配置ID查询")
    public CommonResult<DeviceConfigurationVO> get(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceConfigurationService.get(tenantId, factoryId, id));
    }

    @GetMapping("/by-device-id/{deviceId}")
    @ApiInterceptor
    @Operation(summary = "根据设备ID查询配置")
    public CommonResult<DeviceConfigurationVO> getByDeviceId(@PathVariable("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceConfigurationService.getByDeviceId(tenantId, factoryId, deviceId));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备配置")
    public CommonResult<PageResult<DeviceConfigurationVO>> page(@Valid @RequestBody DeviceConfigurationQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceConfigurationService.page(tenantId, factoryId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:config:delete')")
    @ApiInterceptor
    @Operation(summary = "删除设备配置")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        return CommonResult.success(deviceConfigurationService.delete(tenantId, id));
    }
}

