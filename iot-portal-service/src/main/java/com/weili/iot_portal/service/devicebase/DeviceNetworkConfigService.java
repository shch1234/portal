package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceNetworkConfigVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigUpdateReq;

public interface DeviceNetworkConfigService {

    DeviceNetworkConfigVO create(String tenantId, DeviceNetworkConfigCreateReq request);

    DeviceNetworkConfigVO update(String tenantId, DeviceNetworkConfigUpdateReq request);

    DeviceNetworkConfigVO get(String tenantId, String factoryId, String id);

    DeviceNetworkConfigVO getByDeviceId(String tenantId, String factoryId, String deviceId);

    PageResult<DeviceNetworkConfigVO> page(String tenantId, String factoryId, DeviceNetworkConfigQueryReq request);

    boolean delete(String tenantId, String id);
}

