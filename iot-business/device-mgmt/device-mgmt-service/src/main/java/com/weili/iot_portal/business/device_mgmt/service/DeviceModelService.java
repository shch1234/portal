package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.DeviceModelVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceModelCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceModelQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.DeviceModelUpdateReq;

/**
 * 设备型号服务
 */
public interface DeviceModelService {

    DeviceModelVO create(String tenantId, String operator, DeviceModelCreateReq request);

    DeviceModelVO update(String tenantId, String operator, DeviceModelUpdateReq request);

    DeviceModelVO get(String tenantId, String id);

    PageResult<DeviceModelVO> page(String tenantId, DeviceModelQueryReq request);

    boolean delete(String tenantId, String id);
}


