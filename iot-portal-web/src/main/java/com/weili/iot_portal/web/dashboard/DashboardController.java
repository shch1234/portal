package com.weili.iot_portal.web.dashboard;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.dashboard.AlarmDurationTopRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceListRespVO;
import com.weili.iot_portal.domain.dashboard.DeviceStateStatisticsRespVO;
import com.weili.iot_portal.domain.dashboard.MetricTrendRespVO;
import com.weili.iot_portal.service.dashboard.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 提供设备状态监测相关的统计数据接口
 *
 * @author luying
 * @date 2026-01-12 09:29
 */
@Tag(name = "Dashboard管理")
@RestController
@RequestMapping("/dashboard")
@Validated
public class DashboardController {

    @Resource
    private IDashboardService dashboardService;

    @GetMapping("/device-state")
    @Operation(summary = "获取设备状态统计", description = "获取指定工厂下被监控设备的状态统计数据，包括设备总数、在线数、离线数、故障数")
    @Parameter(name = "code", description = "工厂code", example = "123456789")
    public CommonResult<DeviceStateStatisticsRespVO> getDeviceStateStatistics(@RequestParam(value = "code") Long code) {
        DeviceStateStatisticsRespVO statistics = dashboardService.getDeviceStateStatistics(code);
        return CommonResult.success(statistics);
    }

    @GetMapping("/alarm-duration")
    @Operation(summary = "获取报警时长TOP N", description = "获取指定工厂下报警时长最长的TOP N设备")
    @Parameter(name = "code", description = "工厂code", example = "123456789")
    @Parameter(name = "topN", description = "TOP N数量，默认为5", example = "5")
    public CommonResult<List<AlarmDurationTopRespVO>> getAlarmDurationTop(
            @RequestParam(value = "code") Long code,
            @RequestParam(value = "topN", defaultValue = "5") Integer topN) {
        List<AlarmDurationTopRespVO> result = dashboardService.getAlarmDurationTop(code, topN);
        return CommonResult.success(result);
    }

    @GetMapping("/metric-trend")
    @Operation(summary = "获取指标趋势数据", description = "获取指定工厂在指定时间范围内的指标趋势数据（平均OEE或平均设备利用率）")
    @Parameter(name = "code", description = "工厂code", example = "123456789")
    @Parameter(name = "type", description = "指标类型：OEE-平均OEE, UTILIZATION-平均设备利用率", example = "OEE")
    @Parameter(name = "startDate", description = "开始日期（格式：yyyy-MM-dd）", example = "2026-01-01")
    @Parameter(name = "endDate", description = "结束日期（格式：yyyy-MM-dd）", example = "2026-01-07")
    public CommonResult<MetricTrendRespVO> getMetricTrend(
            @RequestParam(value = "code") Long code,
            @RequestParam(value = "type") String type,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        if (startDate == null || endDate == null) {
            LocalDate now = LocalDate.now();
            endDate = now;
            startDate = now.minusDays(6);
        }
        MetricTrendRespVO result = dashboardService.getMetricTrend(code, type, startDate, endDate);
        return CommonResult.success(result);
    }

    @GetMapping("/device-list")
    @Operation(summary = "获取设备列表", description = "获取指定工厂下的所有被监控设备信息，包括设备ID、设备编码、设备类型、设备实时在线状态")
    @Parameter(name = "code", description = "工厂code", example = "123456789")
    public CommonResult<List<DeviceListRespVO>> getDeviceList(@RequestParam(value = "code") Long code) {
        List<DeviceListRespVO> result = dashboardService.getDeviceList(code);
        return CommonResult.success(result);
    }
}
