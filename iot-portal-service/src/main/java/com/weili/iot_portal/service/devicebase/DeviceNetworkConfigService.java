package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceNetworkConfigVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigUpdateReq;

public interface DeviceNetworkConfigService {

    DeviceNetworkConfigVO create(DeviceNetworkConfigCreateReq request);

    DeviceNetworkConfigVO update(DeviceNetworkConfigUpdateReq request);

    DeviceNetworkConfigVO get(String factoryId, String id);

    DeviceNetworkConfigVO getByDeviceId(String factoryId, String deviceId);

    PageResult<DeviceNetworkConfigVO> page(String factoryId, DeviceNetworkConfigQueryReq request);

    boolean delete(String id);
}

