package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.ProductionHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.ProductionHistoryReq;
import com.weili.iot_portal.business.device_mgmt.service.ProductionStatisticsService;
import com.weili.iot_portal.web.security.context.SecurityFrameworkContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产量统计控制器
 */
@Tag(name = "设备管理-产量统计")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/production")
@RequiredArgsConstructor
public class ProductionStatisticsController {

    private final ProductionStatisticsService productionStatisticsService;

    @GetMapping("/current-shift")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:production:view')")
    @Operation(summary = "查询当前班次产量")
    public CommonResult<ProductionHistoryVO> getCurrentShift(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(productionStatisticsService.getCurrentShift(tenantId, factoryId, deviceId));
    }

    @PostMapping("/shifts-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:production:view')")
    @Operation(summary = "查询班次产量历史")
    public CommonResult<ProductionHistoryVO> getProductionHistory(@PathVariable String deviceId,
                                                                  @Valid @RequestBody ProductionHistoryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(deviceId);
        return CommonResult.success(productionStatisticsService.getHistory(tenantId, factoryId, request));
    }
}


