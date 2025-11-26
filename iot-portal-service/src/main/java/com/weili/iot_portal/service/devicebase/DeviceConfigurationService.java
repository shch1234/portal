package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceConfigurationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationUpdateReq;

/**
 * 设备配置服务
 */
public interface DeviceConfigurationService {

    DeviceConfigurationVO create(String tenantId, DeviceConfigurationCreateReq request);

    DeviceConfigurationVO update(String tenantId, DeviceConfigurationUpdateReq request);

    DeviceConfigurationVO get(String tenantId, String factoryId, String id);

    DeviceConfigurationVO getByDeviceId(String tenantId, String factoryId, String deviceId);

    PageResult<DeviceConfigurationVO> page(String tenantId, String factoryId, DeviceConfigurationQueryReq request);

    boolean delete(String tenantId, String id);
}

