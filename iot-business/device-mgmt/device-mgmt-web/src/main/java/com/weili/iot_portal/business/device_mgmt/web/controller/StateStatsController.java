package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.StateStatsVO;
import com.weili.iot_portal.business.device_mgmt.service.StateStatsService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 状态统计接口
 */
@Tag(name = "设备管理-状态统计")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/state-stats")
@RequiredArgsConstructor
public class StateStatsController {

    private final StateStatsService stateStatsService;

    @GetMapping("/current-shift")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "获取当前班次状态统计")
    public CommonResult<StateStatsVO> getCurrentShiftStats(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateStatsService.getCurrentShiftStats(tenantId, factoryId, deviceId));
    }

    @GetMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询历史状态统计")
    public CommonResult<StateStatsVO> getHistoryStats(@PathVariable String deviceId,
                                                      @RequestParam("startTs") Long startTs,
                                                      @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateStatsService.getHistoryStats(tenantId, factoryId, deviceId, startTs, endTs));
    }
}


