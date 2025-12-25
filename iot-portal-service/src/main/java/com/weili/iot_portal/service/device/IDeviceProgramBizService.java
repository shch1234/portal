package com.weili.iot_portal.service.device;

import com.weili.iot_portal.domain.device.resp.DeviceProgramRespVO;

/**
 * 设备程序信息业务服务接口
 */
public interface IDeviceProgramBizService {

    /**
     * 获取设备程序信息
     *
     * @param deviceId 设备ID
     * @return 设备程序信息，如果没有数据返回null
     */
    DeviceProgramRespVO getDeviceProgram(Long deviceId);
}
