package com.weili.iot_portal.dal.repository.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceRelationPageQuery;

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

