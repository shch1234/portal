package com.weili.iot_portal.business.device_base.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceModelPageQuery;

import java.util.Optional;

/**
 * 设备型号仓储接口
 */
public interface DeviceModelRepository {

    Optional<DeviceModelDO> findById(String tenantId, String id);

    Optional<DeviceModelDO> findByModelCode(String tenantId, String modelCode);

    boolean existsByModelCode(String tenantId, String modelCode, String excludeId);

    PageResult<DeviceModelDO> selectPage(DeviceModelPageQuery query);

    void insert(DeviceModelDO entity);

    void update(DeviceModelDO entity);

    boolean deleteById(String tenantId, String id);
}

