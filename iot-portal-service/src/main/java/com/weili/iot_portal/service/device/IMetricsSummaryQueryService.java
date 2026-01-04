package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.MetricDeviceDataReqVO;
import com.weili.iot_portal.domain.device.req.MetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.MetricDeviceDataRespVO;
import com.weili.iot_portal.domain.device.resp.MetricStatisticsRespVO;

import java.util.List;

/**
 * @InterfaceName: IMetricsSummaryQueryService
 * @Description:
 * @Author: luying
 **/
public interface IMetricsSummaryQueryService {


    /**
     * 查询设备指标统计数据
     * @param reqVO 查询请求参数（设备ID和时间范围）
     * @return 指标统计响应数据（当前指标值 + 趋势图数据）
     */
    MetricStatisticsRespVO getDeviceMetricStatistics(MetricStatisticsReqVO reqVO);

    /**
     * 查询工厂指标统计数据
     * @param reqVO 查询请求参数（时间范围）
     * @return 指标统计响应数据（当前指标值 + 趋势图数据）
     */
    MetricStatisticsRespVO getFactoryMetricStatistics(MetricStatisticsReqVO reqVO);

    /**
     * 查询各指标的TopN设备列表
     * @param reqVO 查询请求参数（工厂ID、日期、TopN数量）
     * @return 各班次的TopN设备列表（按不同指标分类）
     */
    List<MetricDeviceDataRespVO> getDeviceMetricTop(MetricDeviceDataReqVO reqVO);
}
