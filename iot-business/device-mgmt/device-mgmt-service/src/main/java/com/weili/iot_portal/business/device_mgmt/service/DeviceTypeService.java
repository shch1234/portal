package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceTypeVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceTypeUpdateReq;

/**
 * 设备类型服务
 */
public interface DeviceTypeService {

    DeviceTypeVO create(String tenantId, String operator, DeviceTypeCreateReq request);

    DeviceTypeVO update(String tenantId, String operator, DeviceTypeUpdateReq request);

    DeviceTypeVO get(String tenantId, String id);

    PageResult<DeviceTypeVO> page(String tenantId, DeviceTypeQueryReq request);

    boolean delete(String tenantId, String id);
}


