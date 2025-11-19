package com.weili.iot_portal.business.efficiency_mgmt.web.controller;

import com.weili.basic.common.model.CommonResult;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.efficiency_mgmt.domain.model.DeviceEfficiencyMetricVO;
import com.weili.iot_portal.business.efficiency_mgmt.domain.model.request.EfficiencyMetricQueryReq;
import com.weili.iot_portal.business.efficiency_mgmt.service.EfficiencyMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 效率指标控制器
 * 
 * <p>对应需求：4.4 能效管理
 * - 每个指标独立
 * - 查询指定指标（如OEE）在给定时间点（班次）时，所有设备的相关指标
 * - 支持排序
 */
@Tag(name = "效率管理-效率指标", description = "效率指标查询相关接口")
@RestController
@RequestMapping("/api/v1/efficiency/metrics")
@RequiredArgsConstructor
public class EfficiencyMetricController {

    private final EfficiencyMetricService efficiencyMetricService;

    @Operation(
            summary = "查询效率指标列表",
            description = "查询指定指标在指定班次时，所有设备的指标值。\n" +
                    "- 支持5个指标：OEE、时间开动率、性能开动率、设备开动率、停机率\n" +
                    "- 每个指标独立查询\n" +
                    "- 支持按指标值或设备编号排序（升序/降序），可切换排序方式\n" +
                    "- 支持查询当前班次实时指标（不传shiftDate和shiftCode时自动使用当前班次）\n" +
                    "- 支持工厂、车间筛选\n" +
                    "- 支持分页\n" +
                    "- 排序说明：停机率默认升序（越低越好），其他指标默认降序（越高越好）")
    @PostMapping("/query")
    public CommonResult<PageResult<DeviceEfficiencyMetricVO>> getDeviceMetrics(
            @RequestParam("tenantId") String tenantId,
            @RequestBody EfficiencyMetricQueryReq request) {
        PageResult<DeviceEfficiencyMetricVO> result = efficiencyMetricService.getDeviceMetrics(tenantId, request);
        return CommonResult.success(result);
    }

    @Operation(
            summary = "查询当前班次实时指标列表",
            description = "查询指定指标在当前班次时，所有设备的实时指标值。\n" +
                    "这是查询当前班次实时指标的便捷接口，等同于不传shiftDate和shiftCode的/query接口。\n" +
                    "- 自动使用当前班次（根据当前时间计算）\n" +
                    "- 支持5个指标：OEE、时间开动率、性能开动率、设备开动率、停机率\n" +
                    "- 支持排序切换\n" +
                    "- 支持工厂、车间筛选\n" +
                    "- 支持分页")
    @PostMapping("/current")
    public CommonResult<PageResult<DeviceEfficiencyMetricVO>> getCurrentShiftMetrics(
            @RequestParam("tenantId") String tenantId,
            @RequestBody EfficiencyMetricQueryReq request) {
        // 确保不传班次信息，使用当前班次
        request.setShiftDate(null);
        request.setShiftCode(null);
        PageResult<DeviceEfficiencyMetricVO> result = efficiencyMetricService.getDeviceMetrics(tenantId, request);
        return CommonResult.success(result);
    }
}

