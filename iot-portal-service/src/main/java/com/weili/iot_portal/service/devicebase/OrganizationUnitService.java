package com.weili.iot_portal.service.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.domain.devicebase.OrganizationUnitVO;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.domain.devicebase.request.OrganizationUnitUpdateReq;

/**
 * 组织单元服务
 */
public interface OrganizationUnitService {

    OrganizationUnitVO create(String tenantId,OrganizationUnitCreateReq request);

    OrganizationUnitVO update(String tenantId, OrganizationUnitUpdateReq request);

    OrganizationUnitVO get(String tenantId, String id);

    PageResult<OrganizationUnitVO> page(String tenantId, OrganizationUnitQueryReq request);

    boolean delete(String tenantId, String id);
}

