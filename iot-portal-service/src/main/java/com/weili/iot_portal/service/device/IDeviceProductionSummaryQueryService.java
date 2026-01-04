package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceProductionStatisticsReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceProductionStatisticsRespVO;

/**
 * @InterfaceName: IDeviceProductionSummaryQueryService
 * @Description:
 * @Author: luying
 **/
public interface IDeviceProductionSummaryQueryService {


    /**
     * 查询设备产量统计数据
     * @param reqVO 查询请求参数（设备ID和时间范围）
     * @return 产量统计响应数据（当日加工数量 + 趋势图数据）
     */
    DeviceProductionStatisticsRespVO getDeviceProductionStatistics(DeviceProductionStatisticsReqVO reqVO);
}
