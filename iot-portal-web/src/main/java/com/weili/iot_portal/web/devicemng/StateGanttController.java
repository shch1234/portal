package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.StateGanttVO;
import com.weili.iot_portal.service.devicemng.StateGanttService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备管理-状态时间线")
@RestController
@RequestMapping("/device-mgmt/gantt")
@RequiredArgsConstructor
public class StateGanttController {

    private final StateGanttService stateGanttService;

    @GetMapping("/current")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询当前班次状态甘特")
    public CommonResult<StateGanttVO> getCurrentShift(@RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateGanttService.getCurrentShiftGantt(tenantId, factoryId, deviceId));
    }

    @GetMapping
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询历史状态甘特")
    public CommonResult<StateGanttVO> getHistory(@RequestParam("deviceId") String deviceId,
                                                 @RequestParam("startTs") Long startTs,
                                                 @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateGanttService.getHistoryGantt(tenantId, factoryId, deviceId, startTs, endTs));
    }
}



