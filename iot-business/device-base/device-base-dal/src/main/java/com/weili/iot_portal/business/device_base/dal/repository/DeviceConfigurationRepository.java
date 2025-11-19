package com.weili.iot_portal.business.device_base.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceConfigurationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceConfigurationPageQuery;

import java.util.Optional;

/**
 * 设备配置仓储接口
 */
public interface DeviceConfigurationRepository {

    Optional<DeviceConfigurationDO> findById(String tenantId, String id);

    Optional<DeviceConfigurationDO> findByDeviceId(String tenantId, String deviceId);

    boolean existsByDeviceId(String tenantId, String deviceId, String excludeId);

    PageResult<DeviceConfigurationDO> selectPage(DeviceConfigurationPageQuery query);

    void insert(DeviceConfigurationDO entity);

    void update(DeviceConfigurationDO entity);

    boolean deleteById(String tenantId, String id);
}

