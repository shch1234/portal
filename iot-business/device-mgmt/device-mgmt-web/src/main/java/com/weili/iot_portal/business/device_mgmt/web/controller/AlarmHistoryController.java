package com.weili.iot_portal.business.device_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.business.device_mgmt.domain.model.AlarmHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.AlarmHistoryQueryReq;
import com.weili.iot_portal.business.device_mgmt.service.AlarmHistoryService;
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
 * 报警历史接口
 */
@Tag(name = "设备管理-报警历史")
@RestController
@RequestMapping("/api/device-mgmt/v1/devices/{deviceId}/alarms")
@RequiredArgsConstructor
public class AlarmHistoryController {

    private final AlarmHistoryService alarmHistoryService;

    @GetMapping("/current")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:alarm:view')")
    @Operation(summary = "查询进行中的报警")
    public CommonResult<AlarmHistoryVO> getCurrent(@PathVariable String deviceId) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(alarmHistoryService.getCurrentAlarms(tenantId, factoryId, deviceId));
    }

    @PostMapping("/history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:alarm:view')")
    @Operation(summary = "分页查询历史报警")
    public CommonResult<AlarmHistoryVO> getHistory(@PathVariable String deviceId,
                                                   @Valid @RequestBody AlarmHistoryQueryReq request) {
        String tenantId = SecurityFrameworkContext.getLoginTenantId();
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        request.setDeviceId(deviceId);
        return CommonResult.success(alarmHistoryService.getAlarmHistory(tenantId, factoryId, request));
    }
}


