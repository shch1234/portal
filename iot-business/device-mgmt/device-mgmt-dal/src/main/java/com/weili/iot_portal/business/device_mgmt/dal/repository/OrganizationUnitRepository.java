package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.OrganizationUnitDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.OrganizationUnitPageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓储接口
 */
public interface OrganizationUnitRepository {

    Optional<OrganizationUnitDO> findById(String tenantId, String id);

    Optional<OrganizationUnitDO> findByUnitCode(String tenantId, String unitCode);

    List<OrganizationUnitDO> findByParentId(String tenantId, String parentId);

    boolean existsByUnitCode(String tenantId, String unitCode, String excludeId);

    PageResult<OrganizationUnitDO> selectPage(OrganizationUnitPageQuery query);

    void insert(OrganizationUnitDO entity);

    void update(OrganizationUnitDO entity);

    boolean deleteById(String tenantId, String id);
}


