package com.weili.iot_portal.web.devicebase;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicebase.DeviceNetworkConfigVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigUpdateReq;
import com.weili.iot_portal.service.devicebase.DeviceNetworkConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备基础数据-设备网络配置")
@RestController
@RequestMapping("/device-base/device-network-configs")
@RequiredArgsConstructor
public class DeviceNetworkConfigController {

    private final DeviceNetworkConfigService deviceNetworkConfigService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'device:config:create')")
    @ApiInterceptor
    @Operation(summary = "创建设备网络配置")
    public CommonResult<DeviceNetworkConfigVO> create(@Valid @RequestBody DeviceNetworkConfigCreateReq request) {
        return CommonResult.success(deviceNetworkConfigService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:config:update')")
    @ApiInterceptor
    @Operation(summary = "更新设备网络配置")
    public CommonResult<DeviceNetworkConfigVO> update(@PathVariable("id") String id,
                                                      @Valid @RequestBody DeviceNetworkConfigUpdateReq request) {
        request.setId(id);
        return CommonResult.success(deviceNetworkConfigService.update(request));
    }

    @GetMapping("/{id}")
    @ApiInterceptor
    @Operation(summary = "根据配置ID查询")
    public CommonResult<DeviceNetworkConfigVO> get(@PathVariable("id") String id) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceNetworkConfigService.get(factoryId, id));
    }

    @GetMapping("/by-device-id/{deviceId}")
    @ApiInterceptor
    @Operation(summary = "根据设备ID查询配置")
    public CommonResult<DeviceNetworkConfigVO> getByDeviceId(@PathVariable("deviceId") String deviceId) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceNetworkConfigService.getByDeviceId(factoryId, deviceId));
    }

    @PostMapping("/page")
    @ApiInterceptor
    @Operation(summary = "分页查询设备网络配置")
    public CommonResult<PageResult<DeviceNetworkConfigVO>> page(@Valid @RequestBody DeviceNetworkConfigQueryReq request) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        // 位置筛选由 Service 负责反查设备ID
        return CommonResult.success(deviceNetworkConfigService.page(factoryId, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'device:config:delete')")
    @ApiInterceptor
    @Operation(summary = "删除设备网络配置")
    public CommonResult<Boolean> delete(@PathVariable("id") String id) {
        return CommonResult.success(deviceNetworkConfigService.delete(id));
    }
}

