package com.weili.iot_portal.web.digital;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.framework.annotation.ApiInterceptor;
import com.weili.iot_portal.common.framework.SecurityFrameworkContext;
import com.weili.iot_portal.domain.digital.AlarmRankingVO;
import com.weili.iot_portal.domain.digital.FactoryLayoutVO;
import com.weili.iot_portal.domain.digital.FactoryMetricsVO;
import com.weili.iot_portal.domain.digital.FactoryStatusSummaryVO;
import com.weili.iot_portal.service.digital.DigitalScreenQueryApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 数字大屏控制器
 */
@Tag(name = "数字大屏")
@RestController
@RequestMapping("/digital-screen/factories")
@RequiredArgsConstructor
public class DigitalScreenController {

    private final DigitalScreenQueryApi digitalScreenQueryApi;

    @GetMapping("/{factoryId}/layout")
    @Operation(summary = "查询厂区布局图数据")
    @ApiInterceptor
    public CommonResult<FactoryLayoutVO> getFactoryLayout(@PathVariable("factoryId") String factoryId) {
        FactoryLayoutVO result = digitalScreenQueryApi.getFactoryLayout(factoryId);
        return CommonResult.success(result);
    }

    @GetMapping("/{factoryId}/status-summary")
    @Operation(summary = "查询厂区设备状态统计数据")
    @ApiInterceptor
    public CommonResult<FactoryStatusSummaryVO> getFactoryStatusSummary(@PathVariable("factoryId") String factoryId) {
        FactoryStatusSummaryVO result = digitalScreenQueryApi.getFactoryStatusSummary(factoryId);
        return CommonResult.success(result);
    }

    @GetMapping("/{factoryId}/alarm-ranking")
    @Operation(summary = "查询厂区当前报警时长Top")
    @ApiInterceptor
    public CommonResult<java.util.List<AlarmRankingVO>> getAlarmRanking(
            @PathVariable("factoryId") String factoryId,
            @RequestParam(value = "limit", required = false, defaultValue = "5") Integer limit) {
        var result = digitalScreenQueryApi.getAlarmDurationRanking(factoryId, limit);
        return CommonResult.success(result);
    }

    @GetMapping("/{factoryId}/metrics")
    @Operation(summary = "查询工厂级效率指标（平均OEE、平均设备利用率）")
    @ApiInterceptor
    public CommonResult<FactoryMetricsVO> getFactoryMetrics(
            @PathVariable("factoryId") String factoryId,
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days) {
        FactoryMetricsVO result = digitalScreenQueryApi.getFactoryMetrics(factoryId, days);
        return CommonResult.success(result);
    }
}


