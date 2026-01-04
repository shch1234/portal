package com.weili.iot_portal.web.efficiency;

import com.weili.basic.common.enums.ErrorCodeEnum;
import com.weili.basic.common.model.CommonResult;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.domain.device.req.MetricDeviceDataReqVO;
import com.weili.iot_portal.domain.device.req.MetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.MetricDeviceDataRespVO;
import com.weili.iot_portal.domain.device.resp.MetricStatisticsRespVO;
import com.weili.iot_portal.service.device.IMetricsSummaryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    private IMetricsSummaryQueryService metricsSummaryQueryService;

    @PostMapping("/metric-device")
    @Operation(summary = "查询单个设备指标统计",
            description = "查询设备OEE、时间开动率、性能开动率、设备开动率、停机率等指标的当前值和趋势图数据")
    public CommonResult<MetricStatisticsRespVO> getDeviceMetric(@Valid @RequestBody MetricStatisticsReqVO reqVO) {
        if (reqVO.getDeviceId() == null) {
            throw new IotPortalException(ErrorCodeEnum.PARAMS_ILLEGAL, "设备ID不能为空");
        }
        MetricStatisticsRespVO statistics = metricsSummaryQueryService.getDeviceMetricStatistics(reqVO);
        return CommonResult.success(statistics);
    }

    @PostMapping("/metric-factory")
    @Operation(summary = "查询工厂指标统计",
            description = "查询工厂OEE、时间开动率、性能开动率、设备开动率、停机率等指标的当前值和趋势图数据")
    public CommonResult<MetricStatisticsRespVO> getListMetric(@Valid @RequestBody MetricStatisticsReqVO reqVO) {
        MetricStatisticsRespVO statistics = metricsSummaryQueryService.getFactoryMetricStatistics(reqVO);
        return CommonResult.success(statistics);
    }

    @PostMapping("/metric-device-top")
    @Operation(summary = "查询指标的topN设备",
            description = "查询设备OEE、时间开动率、性能开动率、设备开动率、停机率等指标的对应的topN的设备列表信息")
    public CommonResult<List<MetricDeviceDataRespVO>> getDeviceMetricTop(@Valid @RequestBody MetricDeviceDataReqVO reqVO) {
        List<MetricDeviceDataRespVO> result = metricsSummaryQueryService.getDeviceMetricTop(reqVO);
        return CommonResult.success(result);
    }
}
