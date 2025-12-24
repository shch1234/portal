package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.req.DeviceToolCompensationQueryReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceToolCompensationRespVO;

import java.util.List;

/**
 * 设备刀具补偿业务接口
 */
public interface IDeviceToolCompensationBizService {

    /**
     * 获取设备的刀具补偿列表
     *
     * @param queryReqVO 查询请求参数
     * @return 刀具补偿响应VO
     */
    List<DeviceToolCompensationRespVO> getDeviceToolCompensation(DeviceToolCompensationQueryReqVO queryReqVO);
}
