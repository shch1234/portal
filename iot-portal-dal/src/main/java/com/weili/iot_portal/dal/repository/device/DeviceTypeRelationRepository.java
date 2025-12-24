package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 设备类型仓储接口
 */
public interface DeviceTypeRelationRepository {

    Optional<DeviceTypeRelationDO> findById(Long id);

    Optional<DeviceTypeRelationDO> findByTypeCode(String typeCode);

    List<DeviceTypeRelationDO> findByParentTypeId(Long parentTypeId);

    List<DeviceTypeRelationDO> findByParentTypeCode(String parentTypeCode);


    boolean existsByTypeCode(String typeCode, Long excludeId);

    PageResult<DeviceTypeRelationDO> selectPage(DeviceTypePageQuery query);

    void insert(DeviceTypeRelationDO entity);

    void update(DeviceTypeRelationDO entity);

    boolean deleteById(Long id);
}

