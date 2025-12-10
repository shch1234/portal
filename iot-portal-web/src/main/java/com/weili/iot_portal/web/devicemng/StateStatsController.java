package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.StateStatsVO;
import com.weili.iot_portal.service.devicemng.StateStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 状态统计接口
 */
@Tag(name = "设备管理-状态统计")
@RestController
@RequestMapping("/device-mgmt/state-stats")
@RequiredArgsConstructor
public class StateStatsController {

    private final StateStatsService stateStatsService;

    @GetMapping("/current-shift")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "获取当前班次状态统计")
    public CommonResult<StateStatsVO> getCurrentShiftStats(@RequestParam("deviceId") String deviceId) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateStatsService.getCurrentShiftStats(factoryId, deviceId));
    }

    @GetMapping("/history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:state:view')")
    @Operation(summary = "查询历史状态统计")
    public CommonResult<StateStatsVO> getHistoryStats(@RequestParam("deviceId") String deviceId,
                                                      @RequestParam("startTs") Long startTs,
                                                      @RequestParam("endTs") Long endTs) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(stateStatsService.getHistoryStats(factoryId, deviceId, startTs, endTs));
    }
}


