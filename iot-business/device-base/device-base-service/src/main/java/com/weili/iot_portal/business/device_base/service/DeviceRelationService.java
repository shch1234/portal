package com.weili.iot_portal.business.device_base.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.domain.model.DeviceRelationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationCreateReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationQueryReq;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceRelationUpdateReq;

/**
 * 设备关系服务
 */
public interface DeviceRelationService {

    DeviceRelationVO create(String tenantId, String operator, DeviceRelationCreateReq request);

    DeviceRelationVO update(String tenantId, String operator, DeviceRelationUpdateReq request);

    DeviceRelationVO get(String tenantId, String id);

    PageResult<DeviceRelationVO> page(String tenantId, DeviceRelationQueryReq request);

    boolean delete(String tenantId, String id);
}

