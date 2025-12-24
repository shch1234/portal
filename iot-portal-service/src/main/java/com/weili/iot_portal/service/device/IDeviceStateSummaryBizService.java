package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceStateSummaryQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceStateSummaryRespVO;

/**
 * 设备状态汇总业务服务接口
 */
public interface IDeviceStateSummaryBizService {

    /**
     * 查询设备状态统计（包含饼图数据和时间轴数据）
     *
     * @param queryReqVO 查询请求
     * @return 设备状态统计响应
     */
    DeviceStateSummaryRespVO getDeviceStateSummary(DeviceStateSummaryQueryReqVO queryReqVO);
}
