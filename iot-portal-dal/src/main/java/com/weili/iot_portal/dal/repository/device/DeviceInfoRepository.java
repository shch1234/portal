package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备基础信息仓储接口
 */
public interface DeviceInfoRepository {

    Optional<DeviceInfoDO> findById(Long id);

    Optional<DeviceInfoDO> findByDeviceCode(String deviceCode);

    boolean existsByDeviceCode(String deviceCode, Long excludeId);

    PageResult<DeviceInfoDO> selectPage(DeviceBaseInfoPageQuery query);

    List<DeviceInfoDO> findByFactoryId(String factoryId);

    Optional<DeviceInfoDO> findActiveMonitoredByDeviceCode(String deviceCode);

    /**
     * 查询所有未删除的设备
     */
    List<DeviceInfoDO> findAllActive();

    /**
     * 查询未删除且已关联工厂的设备
     */
    List<DeviceInfoDO> findActiveWithFactory();

    void insert(DeviceInfoDO entity);

    void update(DeviceInfoDO entity);

    boolean deleteById(Long id);
}

