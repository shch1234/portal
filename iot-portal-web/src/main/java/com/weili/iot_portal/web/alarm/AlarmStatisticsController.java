package com.weili.iot_portal.web.alarm;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.alarm.AlarmDeviceItemVO;
import com.weili.iot_portal.domain.alarm.CurrentAlarmDeviceCountVO;
import com.weili.iot_portal.domain.alarm.request.CurrentAlarmDeviceQueryReq;
import com.weili.iot_portal.service.alarm.AlarmStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 报警统计控制器
 */
@Tag(name = "报警统计", description = "报警统计相关接口")
@RestController
@RequestMapping("/alarm/statistics")
@RequiredArgsConstructor
public class AlarmStatisticsController {

    private final AlarmStatisticsService alarmStatisticsService;

    @Operation(summary = "获取当前报警设备数量", description = "统计指定工厂（或车间）内所有正在报警的设备个数。注意：一台设备可能有多个报警，但只统计设备个数（去重）。")
    @GetMapping("/current-count")
    public CommonResult<CurrentAlarmDeviceCountVO> getCurrentAlarmDeviceCount(
            @RequestParam("tenantId") String tenantId,
            @RequestParam("factoryId") String factoryId,
            @RequestParam(value = "workshopId", required = false) String workshopId) {
        CurrentAlarmDeviceCountVO result = alarmStatisticsService.getCurrentAlarmDeviceCount(
                tenantId, factoryId, workshopId);
        return CommonResult.success(result);
    }

    @Operation(summary = "查询当前报警设备列表", description = "查询正在报警的设备列表，每个设备显示其报警数量。支持车间筛选和分页。")
    @PostMapping("/current-devices")
    public CommonResult<PageResult<AlarmDeviceItemVO>> getCurrentAlarmDeviceList(
            @RequestParam("tenantId") String tenantId,
            @RequestBody CurrentAlarmDeviceQueryReq request) {
        PageResult<AlarmDeviceItemVO> result = alarmStatisticsService.getCurrentAlarmDeviceList(tenantId, request);
        return CommonResult.success(result);
    }
}

