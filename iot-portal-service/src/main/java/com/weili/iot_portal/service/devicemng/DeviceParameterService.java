package com.weili.iot_portal.service.devicemng;

import com.weili.iot_portal.domain.devicemng.DeviceParameterHistoryVO;
import com.weili.iot_portal.domain.devicemng.DeviceParameterVO;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.domain.devicemng.request.DeviceParameterUpdateReq;

/**
 * 设备参数服务
 */
public interface DeviceParameterService {

    DeviceParameterVO getCurrent(String factoryId, String deviceId);

    DeviceParameterVO updateParameters(String factoryId, String deviceId, DeviceParameterUpdateReq request);

    DeviceParameterHistoryVO getHistory(String factoryId, DeviceParameterHistoryReq request);
}


