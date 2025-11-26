package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.DeviceMetricHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.DeviceMetricHistoryReq;
import com.weili.iot_portal.service.devicemng.DeviceMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备指标接口
 */
@Tag(name = "设备管理-设备指标")
@RestController
@RequestMapping("/device-mgmt/metrics")
@RequiredArgsConstructor
public class DeviceMetricsController {

    private final DeviceMetricsService deviceMetricsService;

    @GetMapping("/current")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:metrics:view')")
    @Operation(summary = "获取当前班次指标")
    public CommonResult<DeviceMetricHistoryVO> getCurrentMetrics(@RequestParam("deviceId") String deviceId,
                                                                 @RequestParam(value = "metricCodes", required = false)
                                                                 List<String> metricCodes) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(deviceMetricsService.getCurrentMetrics(tenantId, factoryId, deviceId, metricCodes));
    }

    @PostMapping("/shifts-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:metrics:view')")
    @Operation(summary = "查询历史班次指标")
    public CommonResult<DeviceMetricHistoryVO> getShiftMetrics(@RequestParam("deviceId") String deviceId,
                                                               @Valid @RequestBody DeviceMetricHistoryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(deviceId);
        return CommonResult.success(deviceMetricsService.getShiftMetrics(tenantId, factoryId, request));
    }
}





