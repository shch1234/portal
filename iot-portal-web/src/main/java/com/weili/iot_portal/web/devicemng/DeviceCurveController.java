package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.RealtimeCurveVO;
import com.weili.iot_portal.domain.devicemng.RealtimeMetricValueVO;
import com.weili.iot_portal.service.devicemng.RealtimeCurveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备曲线接口
 */
@Tag(name = "设备管理-实时曲线")
@RestController
@RequestMapping("/devices/{deviceId}/curves")
@RequiredArgsConstructor
public class DeviceCurveController {

    private final RealtimeCurveService realtimeCurveService;

    @GetMapping("/spindle-load")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "实时主轴负载曲线")
    public CommonResult<RealtimeCurveVO> getRealtimeSpindleLoad(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getRealtimeSpindleLoad(tenantId, factoryId, deviceId));
    }

    @GetMapping("/spindle-load-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "历史主轴负载曲线")
    public CommonResult<RealtimeCurveVO> getHistorySpindleLoad(@PathVariable String deviceId,
                                                               @RequestParam("startTs") Long startTs,
                                                               @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getHistorySpindleLoad(tenantId, factoryId, deviceId, startTs, endTs));
    }

    @GetMapping("/spindle-speed")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "实时主轴转速曲线")
    public CommonResult<RealtimeCurveVO> getRealtimeSpindleSpeed(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getRealtimeSpindleSpeed(tenantId, factoryId, deviceId));
    }

    @GetMapping("/spindle-speed-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "历史主轴转速曲线")
    public CommonResult<RealtimeCurveVO> getHistorySpindleSpeed(@PathVariable String deviceId,
                                                                @RequestParam("startTs") Long startTs,
                                                                @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getHistorySpindleSpeed(tenantId, factoryId, deviceId, startTs, endTs));
    }

    @GetMapping("/feed-rate")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "实时进给率曲线")
    public CommonResult<RealtimeCurveVO> getRealtimeFeedRate(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getRealtimeFeedRate(tenantId, factoryId, deviceId));
    }

    @GetMapping("/feed-rate-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "历史进给率曲线")
    public CommonResult<RealtimeCurveVO> getHistoryFeedRate(@PathVariable String deviceId,
                                                            @RequestParam("startTs") Long startTs,
                                                            @RequestParam("endTs") Long endTs) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getHistoryFeedRate(tenantId, factoryId, deviceId, startTs, endTs));
    }

    @GetMapping("/feed-override")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:curve:view')")
    @Operation(summary = "实时进给倍率")
    public CommonResult<RealtimeMetricValueVO> getFeedOverride(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(realtimeCurveService.getRealtimeFeedOverride(tenantId, factoryId, deviceId));
    }
}


