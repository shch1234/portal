package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.AxisCoordinateListVO;
import com.weili.iot_portal.service.devicemng.AxisCoordinateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 轴坐标控制器
 */
@Tag(name = "设备管理-运行参数")
@RestController
@RequestMapping("/device-mgmt/axis-coordinates")
@RequiredArgsConstructor
public class AxisCoordinateController {

    private final AxisCoordinateService axisCoordinateService;

    @GetMapping
    @ApiInterceptor
    @Operation(summary = "获取设备当前轴坐标列表")
    public CommonResult<AxisCoordinateListVO> getAxisCoordinates(@RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(axisCoordinateService.getCurrentAxisCoordinates(tenantId, factoryId, deviceId));
    }
}

