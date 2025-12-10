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

    OrganizationUnitVO create(OrganizationUnitCreateReq request);

    OrganizationUnitVO update(OrganizationUnitUpdateReq request);

    OrganizationUnitVO get(String id);

    PageResult<OrganizationUnitVO> page(OrganizationUnitQueryReq request);

    boolean delete(String id);
}

