package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.service.devicemng.DeviceProductionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备管理-产量查询")
@RestController
@RequestMapping("/production")
@RequiredArgsConstructor
public class DeviceProductionController {

    private final DeviceProductionQueryService deviceProductionQueryService;

    @Operation(summary = "查询当前班次已完成产量（end_ts 落在当前班次范围）")
    @GetMapping("/current-shift/completed")
    public CommonResult<Long> getCurrentShiftCompleted(@RequestParam("factoryId") String factoryId,
                                                       @RequestParam("deviceId") String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        long count = deviceProductionQueryService.currentShiftCompletedCount(tenantId, factoryId, deviceId);
        return CommonResult.success(count);
    }
}


