package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceBaseInfoListVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceBaseInfoQueryReq;
import com.weili.iot_portal.business.device_mgmt.service.DeviceBaseInfoService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备列表控制器
 */
@Tag(name = "设备管理-设备列表")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices")
@RequiredArgsConstructor
public class DeviceListController {

    private final DeviceBaseInfoService deviceBaseInfoService;

    @PostMapping("/list")
    @Operation(summary = "设备列表查询（用于列表页面展示，包含实时状态和报警状态）")
    @ApiInterceptor
    public CommonResult<PageResult<DeviceBaseInfoListVO>> list(@Valid @RequestBody DeviceBaseInfoQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        PageResult<DeviceBaseInfoListVO> result = deviceBaseInfoService.list(tenantId, factoryId, request);
        return CommonResult.success(result);
    }
}

