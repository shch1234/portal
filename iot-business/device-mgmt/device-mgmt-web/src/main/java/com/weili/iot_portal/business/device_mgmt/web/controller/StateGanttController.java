package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.StateGanttVO;
import com.weili.iot_portal.business.device_mgmt.service.StateGanttService;
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
 * 状态甘特接口
 */
@Tag(name = "设备管理-状态时间线")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/state-gantt")
@RequiredArgsConstructor
public class StateGanttController {

    private final StateGanttService stateGanttService;

    @GetMapping("/current-shift")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询当前班次状态甘特")
    public CommonResult<StateGanttVO> getCurrentShift(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateGanttService.getCurrentShiftGantt(tenantId, factoryId, deviceId));
    }

    @GetMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询历史状态甘特")
    public CommonResult<StateGanttVO> getHistory(@PathVariable String deviceId,
                                                 @RequestParam("startTs") Long startTs,
                                                 @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateGanttService.getHistoryGantt(tenantId, factoryId, deviceId, startTs, endTs));
    }
}


