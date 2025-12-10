package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.DeviceParameterHistoryVO;
import com.weili.iot_portal.domain.devicemng.DeviceParameterVO;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterUpdateReq;
import com.weili.iot_portal.service.devicemng.DeviceParameterService;
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
@RequestMapping("/device-mgmt/parameters")
@RequiredArgsConstructor
public class DeviceParameterController {

    private final DeviceParameterService deviceParameterService;

    @GetMapping("/current")
    @ApiInterceptor
    @Operation(summary = "获取当前设备参数")
    public CommonResult<DeviceParameterVO> getParameters(@RequestParam("deviceId") String deviceId) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceParameterService.getCurrent(factoryId, deviceId));
    }

    @PutMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:param:update')")
    @Operation(summary = "更新设备参数")
    public CommonResult<DeviceParameterVO> updateParameters(@Valid @RequestBody DeviceParameterUpdateReq request) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceParameterService.updateParameters(factoryId, request.getDeviceId(), request));
    }

    @PostMapping("/history")
    @ApiInterceptor
    @Operation(summary = "查询设备参数历史")
    public CommonResult<DeviceParameterHistoryVO> history(@Valid @RequestBody DeviceParameterHistoryReq request) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(request.getDeviceId());
        return CommonResult.success(deviceParameterService.getHistory(factoryId, request));
    }
}


