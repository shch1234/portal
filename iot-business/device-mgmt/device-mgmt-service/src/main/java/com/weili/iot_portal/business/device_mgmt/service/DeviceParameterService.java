package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterHistoryVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceParameterVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterHistoryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceParameterUpdateReq;

/**
 * 设备参数服务
 */
public interface DeviceParameterService {

    DeviceParameterVO getCurrent(String tenantId, String factoryId, String deviceId);

    DeviceParameterVO updateParameters(String tenantId, String factoryId, String deviceId, String operator, DeviceParameterUpdateReq request);

    DeviceParameterHistoryVO getHistory(String tenantId, String factoryId, DeviceParameterHistoryReq request);
}


