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

    /**
     * 根据设备编号查询活跃设备（用于设备匹配）
     * <p>
     * 匹配条件：
     * 1. device_code 匹配
     * 2. deleted = 0（未删除）
     * 3. device_status = 'ACTIVE'（状态为ACTIVE）
     * </p>
     *
     * @param deviceCode 设备编号
     * @return 设备信息，如果未匹配则返回空
     */
    Optional<DeviceInfoDO> findActiveMonitoredByDeviceCode(String deviceCode);

    /**
     * 查询所有未删除的设备
     */
    List<DeviceInfoDO> findAllActive();

    /**
     * 查询未删除且已关联工厂的设备
     */
    List<DeviceInfoDO> findActiveWithFactory();

    /**
     * 批量查询设备信息（根据ID列表）
     *
     * @param ids 设备ID列表
     * @return 设备信息列表
     */
    List<DeviceInfoDO> selectByIds(List<Long> ids);

    void insert(DeviceInfoDO entity);

    void update(DeviceInfoDO entity);

    boolean deleteById(Long id);
}

