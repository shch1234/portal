package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.CurrentToolInfoVO;
import com.weili.iot_portal.domain.devicemng.ToolCompensationVO;
import com.weili.iot_portal.domain.devicemng.ToolUsageHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.ToolUsageHistoryReq;
import com.weili.iot_portal.service.devicemng.ToolCompensationService;
import com.weili.iot_portal.service.devicemng.ToolInfoService;
import com.weili.iot_portal.service.devicemng.ToolUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备管理-刀具信息")
@RestController
@RequestMapping("/device-mgmt/tool")
@RequiredArgsConstructor
public class DeviceToolController {

    private final ToolInfoService toolInfoService;
    private final ToolUsageService toolUsageService;
    private final ToolCompensationService toolCompensationService;

    @GetMapping("/current")
    @Operation(summary = "查询当前刀具信息")
    public CommonResult<CurrentToolInfoVO> getCurrent(@RequestParam("deviceId")  String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(toolInfoService.getCurrentToolInfo(tenantId, factoryId, deviceId));
    }

    @GetMapping("/usage/history")
    @Operation(summary = "查询刀具使用记录")
    public CommonResult<ToolUsageHistoryVO> getUsageHistory(@RequestParam("deviceId") String deviceId,
                                                            @RequestParam Long startTs,
                                                            @RequestParam Long endTs,
                                                            @RequestParam(required = false) Integer limit) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        ToolUsageHistoryReq req = new ToolUsageHistoryReq();
        req.setDeviceId(deviceId);
        req.setStartTs(startTs);
        req.setEndTs(endTs);
        req.setLimit(limit);
        return CommonResult.success(toolUsageService.getHistory(tenantId, factoryId, req));
    }

    @GetMapping("/compensation")
    @Operation(summary = "查询刀具补偿列表")
    public CommonResult<ToolCompensationVO> getCompensation(@RequestParam("deviceId")  String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(toolCompensationService.getCurrent(tenantId, factoryId, deviceId));
    }
}

