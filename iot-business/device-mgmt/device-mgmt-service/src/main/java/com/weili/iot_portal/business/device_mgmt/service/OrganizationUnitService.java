package com.weili.iot_portal.business.device_mgmt.service;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.domain.model.OrganizationUnitVO;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.OrganizationUnitCreateReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.OrganizationUnitQueryReq;
import com.weili.iot_portal.business.device_mgmt.domain.model.request.OrganizationUnitUpdateReq;

/**
 * 组织单元服务
 */
public interface OrganizationUnitService {

    OrganizationUnitVO create(String tenantId, String operator, OrganizationUnitCreateReq request);

    OrganizationUnitVO update(String tenantId, String operator, OrganizationUnitUpdateReq request);

    OrganizationUnitVO get(String tenantId, String id);

    PageResult<OrganizationUnitVO> page(String tenantId, OrganizationUnitQueryReq request);

    boolean delete(String tenantId, String id);
}


