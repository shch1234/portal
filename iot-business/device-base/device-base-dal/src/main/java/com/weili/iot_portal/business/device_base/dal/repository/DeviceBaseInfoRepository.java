package com.weili.iot_portal.business.device_base.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceBaseInfoPageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备基础信息仓储接口
 */
public interface DeviceBaseInfoRepository {

    Optional<DeviceBaseInfoDO> findById(String tenantId, String id);

    Optional<DeviceBaseInfoDO> findByDeviceCode(String tenantId, String deviceCode);

    Optional<DeviceBaseInfoDO> findByTbDeviceId(String tenantId, String tbDeviceId);

    boolean existsByDeviceCode(String tenantId, String deviceCode, String excludeId);

    boolean existsByTbDeviceId(String tenantId, String tbDeviceId, String excludeId);

    PageResult<DeviceBaseInfoDO> selectPage(DeviceBaseInfoPageQuery query);

    List<DeviceBaseInfoDO> findByFactoryId(String tenantId, String factoryId);

    void insert(DeviceBaseInfoDO entity);

    void update(DeviceBaseInfoDO entity);

    boolean deleteById(String tenantId, String id);
}

