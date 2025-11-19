package com.weili.iot_portal.business.device_base.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.domain.model.DeviceConfigurationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationUpdateReq;

/**
 * 设备配置服务
 */
public interface DeviceConfigurationService {

    DeviceConfigurationVO create(String tenantId, String operator, DeviceConfigurationCreateReq request);

    DeviceConfigurationVO update(String tenantId, String operator, DeviceConfigurationUpdateReq request);

    DeviceConfigurationVO get(String tenantId, String factoryId, String id);

    DeviceConfigurationVO getByDeviceId(String tenantId, String factoryId, String deviceId);

    PageResult<DeviceConfigurationVO> page(String tenantId, String factoryId, DeviceConfigurationQueryReq request);

    boolean delete(String tenantId, String id);
}

