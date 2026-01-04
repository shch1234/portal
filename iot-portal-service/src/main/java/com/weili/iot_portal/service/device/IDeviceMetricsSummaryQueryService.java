package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceMetricStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceMetricStatisticsRespVO;

/**
 * @InterfaceName: IDeviceMetricsSummaryQueryService
 * @Description:
 * @Author: luying
 **/
public interface IDeviceMetricsSummaryQueryService {


    /**
     * 查询设备指标统计数据
     * @param reqVO 查询请求参数（设备ID和时间范围）
     * @return 指标统计响应数据（当前指标值 + 趋势图数据）
     */
    DeviceMetricStatisticsRespVO getDeviceMetricStatistics(DeviceMetricStatisticsReqVO reqVO);
}
