package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceTypeVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceTypeUpdateReq;

/**
 * 设备类型服务
 */
public interface DeviceTypeService {

    DeviceTypeVO create(String tenantId,DeviceTypeCreateReq request);

    DeviceTypeVO update(String tenantId,  DeviceTypeUpdateReq request);

    DeviceTypeVO get(String tenantId, String id);

    PageResult<DeviceTypeVO> page(String tenantId, DeviceTypeQueryReq request);

    boolean delete(String tenantId, String id);
}

