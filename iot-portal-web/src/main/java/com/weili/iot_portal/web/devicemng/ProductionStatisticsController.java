package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.ProductionHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.ProductionHistoryReq;
import com.weili.iot_portal.service.devicemng.ProductionStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 产量统计控制器
 */
@Tag(name = "设备管理-产量统计")
@RestController
@RequestMapping("/device-mgmt/production")
@RequiredArgsConstructor
public class ProductionStatisticsController {

    private final ProductionStatisticsService productionStatisticsService;

    @GetMapping("/current-shift")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:production:view')")
    @Operation(summary = "查询当前班次产量")
    public CommonResult<ProductionHistoryVO> getCurrentShift(@RequestParam("deviceId") String deviceId) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(productionStatisticsService.getCurrentShift(factoryId, deviceId));
    }

    @PostMapping("/shifts-history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:production:view')")
    @Operation(summary = "查询班次产量历史")
    public CommonResult<ProductionHistoryVO> getProductionHistory(@RequestParam("deviceId") String deviceId,
                                                                  @Valid @RequestBody ProductionHistoryReq request) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(deviceId);
        return CommonResult.success(productionStatisticsService.getHistory(factoryId, request));
    }
}


