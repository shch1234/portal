package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceModelVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceModelUpdateReq;

/**
 * 设备型号服务
 */
public interface DeviceModelService {

    DeviceModelVO create(DeviceModelCreateReq request);

    DeviceModelVO update(DeviceModelUpdateReq request);

    DeviceModelVO get(String id);

    PageResult<DeviceModelVO> page(DeviceModelQueryReq request);

    boolean delete(String id);
}

