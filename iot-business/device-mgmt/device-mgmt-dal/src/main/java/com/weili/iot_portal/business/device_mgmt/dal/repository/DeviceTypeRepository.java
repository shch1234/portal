package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceTypeDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.DeviceTypePageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备类型仓储接口
 */
public interface DeviceTypeRepository {

    Optional<DeviceTypeDO> findById(String tenantId, String id);

    Optional<DeviceTypeDO> findByTypeCode(String tenantId, String typeCode);

    List<DeviceTypeDO> findByParentTypeId(String tenantId, String parentTypeId);

    boolean existsByTypeCode(String tenantId, String typeCode, String excludeId);

    PageResult<DeviceTypeDO> selectPage(DeviceTypePageQuery query);

    void insert(DeviceTypeDO entity);

    void update(DeviceTypeDO entity);

    boolean deleteById(String tenantId, String id);
}


