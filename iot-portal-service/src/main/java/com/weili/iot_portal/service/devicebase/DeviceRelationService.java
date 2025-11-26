package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.DeviceRelationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationCreateReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationQueryReq;
import com.weili.iot_portal.domain.devicebase.request.DeviceRelationUpdateReq;

/**
 * 设备关系服务
 */
public interface DeviceRelationService {

    DeviceRelationVO create(String tenantId, DeviceRelationCreateReq request);

    DeviceRelationVO update(String tenantId, DeviceRelationUpdateReq request);

    DeviceRelationVO get(String tenantId, String id);

    PageResult<DeviceRelationVO> page(String tenantId, DeviceRelationQueryReq request);

    boolean delete(String tenantId, String id);
}

