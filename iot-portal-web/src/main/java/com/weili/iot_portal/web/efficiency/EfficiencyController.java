package com.weili.iot_portal.web.efficiency;

import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.domain.device.req.DeviceMetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceMetricStatisticsRespVO;
import com.weili.iot_portal.service.device.IDeviceMetricsSummaryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author luying
 * @className EfficiencyController
 * @description 能效管理
 * @date 2026-01-04 13:59
 **/
@Tag(name = "能效管理")
@RestController
@RequestMapping("/efficiency-mgmt")
@Validated
public class EfficiencyController {

    @Resource
    private IDeviceMetricsSummaryQueryService deviceMetricsSummaryQueryService;

    @PostMapping("/metric-statistics")
    @Operation(summary = "查询设备指标统计",
            description = "查询设备OEE、时间开动率、性能开动率、设备开动率、停机率等指标的当前值和趋势图数据")
    public CommonResult<DeviceMetricStatisticsRespVO> getMetricStatistics(@Valid @RequestBody DeviceMetricStatisticsReqVO reqVO) {
        DeviceMetricStatisticsRespVO statistics = deviceMetricsSummaryQueryService.getDeviceMetricStatistics(reqVO);
        return CommonResult.success(statistics);
    }

}
