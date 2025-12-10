package com.weili.iot_portal.dal.repository.devicebase;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备基础信息仓储接口
 */
public interface DeviceBaseInfoRepository {

    Optional<DeviceBaseInfoDO> findById(String id);

    Optional<DeviceBaseInfoDO> findByDeviceCode(String deviceCode);

    Optional<DeviceBaseInfoDO> findByTbDeviceId(String tbDeviceId);

    boolean existsByDeviceCode(String deviceCode, String excludeId);

    boolean existsByTbDeviceId(String tbDeviceId, String excludeId);

    PageResult<DeviceBaseInfoDO> selectPage(DeviceBaseInfoPageQuery query);

    List<DeviceBaseInfoDO> findByFactoryId(String factoryId);

    void insert(DeviceBaseInfoDO entity);

    void update(DeviceBaseInfoDO entity);

    boolean deleteById(String id);
}

