package com.weili.iot_portal.web.devicemng;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.devicemng.AlarmHistoryVO;
import com.weili.iot_portal.domain.devicemng.request.AlarmHistoryQueryReq;
import com.weili.iot_portal.service.devicemng.AlarmHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 报警历史接口
 */
@Tag(name = "设备管理-报警历史")
@RestController
@RequestMapping("/device-mgmt/alarms")
@RequiredArgsConstructor
public class AlarmHistoryController {

    private final AlarmHistoryService alarmHistoryService;

    @GetMapping("/current")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:alarm:view')")
    @Operation(summary = "查询进行中的报警")
    public CommonResult<AlarmHistoryVO> getCurrent(@RequestParam("deviceId")  String deviceId) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(alarmHistoryService.getCurrentAlarms(factoryId, deviceId));
    }

    @PostMapping("/history")
    @ApiInterceptor
    @PreAuthorize("hasPermission(null, 'device:alarm:view')")
    @Operation(summary = "分页查询历史报警")
    public CommonResult<AlarmHistoryVO> getHistory( @Valid @RequestBody AlarmHistoryQueryReq request) {
        String factoryId = SecurityFrameworkContext.getLoginFactoryId();
        return CommonResult.success(alarmHistoryService.getAlarmHistory(factoryId, request));
    }
}


