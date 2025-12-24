package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceAxisQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceAxisRespVO;

/**
 * 设备轴标签信息业务服务接口
 */
public interface IDeviceAxisBizService {

    /**
     * 获取设备轴标签信息（包含主轴曲线和轴坐标）
     *
     * @param queryReqVO 查询请求
     * @return 设备轴标签信息响应
     */
    DeviceAxisRespVO getDeviceAxisInfo(DeviceAxisQueryReqVO queryReqVO);
}
