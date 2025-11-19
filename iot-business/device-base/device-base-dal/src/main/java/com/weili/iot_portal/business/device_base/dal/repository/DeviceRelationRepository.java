package com.weili.iot_portal.business.device_base.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceRelationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceRelationPageQuery;

import java.util.Optional;

/**
 * 设备关系仓储
 */
public interface DeviceRelationRepository {

    Optional<DeviceRelationDO> findById(String tenantId, String id);

    PageResult<DeviceRelationDO> selectPage(DeviceRelationPageQuery query);

    boolean existsRelation(String tenantId, String fromDeviceId, String toDeviceId, String relationType, String excludeId);

    void insert(DeviceRelationDO entity);

    void update(DeviceRelationDO entity);

    boolean deleteById(String tenantId, String id);
}

