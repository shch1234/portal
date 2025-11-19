package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.CurrentToolInfoVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ToolCompensationVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.ToolUsageHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ToolUsageHistoryReq;
import com.weili.iot_portal.business.device_mgmt.service.ToolInfoService;
import com.weili.iot_portal.business.device_mgmt.service.ToolCompensationService;
import com.weili.iot_portal.business.device_mgmt.service.ToolUsageService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备管理-刀具信息")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/tool")
@RequiredArgsConstructor
public class DeviceToolController {

    private final ToolInfoService toolInfoService;
    private final ToolUsageService toolUsageService;
    private final ToolCompensationService toolCompensationService;

    @GetMapping("/current")
    @Operation(summary = "查询当前刀具信息")
    public CommonResult<CurrentToolInfoVO> getCurrent(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(toolInfoService.getCurrentToolInfo(tenantId, factoryId, deviceId));
    }

    @GetMapping("/usage/history")
    @Operation(summary = "查询刀具使用记录")
    public CommonResult<ToolUsageHistoryVO> getUsageHistory(@PathVariable String deviceId,
                                                            @RequestParam Long startTs,
                                                            @RequestParam Long endTs,
                                                            @RequestParam(required = false) Integer limit) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        ToolUsageHistoryReq req = new ToolUsageHistoryReq();
        req.setDeviceId(deviceId);
        req.setStartTs(startTs);
        req.setEndTs(endTs);
        req.setLimit(limit);
        return CommonResult.success(toolUsageService.getHistory(tenantId, factoryId, req));
    }

    @GetMapping("/compensation")
    @Operation(summary = "查询刀具补偿列表")
    public CommonResult<ToolCompensationVO> getCompensation(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(toolCompensationService.getCurrent(tenantId, factoryId, deviceId));
    }
}

