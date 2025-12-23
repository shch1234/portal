package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.dal.ddd.device.DeviceModelPageQuery;

import java.util.Optional;

/**
 * 设备型号仓储接口
 */
public interface DeviceModelRepository {

    Optional<DeviceModelDO> findById(Long id);

    Optional<DeviceModelDO> findByModelCode(String modelCode);

    boolean existsByModelCode(String modelCode, Long id);

    PageResult<DeviceModelDO> selectPage(DeviceModelPageQuery query);

    void insert(DeviceModelDO entity);

    void update(DeviceModelDO entity);

    boolean deleteById(Long id);
}

