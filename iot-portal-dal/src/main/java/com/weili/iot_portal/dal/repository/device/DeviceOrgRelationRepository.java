package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceOrgRelationPageQuery;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓储接口
 */
public interface DeviceOrgRelationRepository {

    Optional<DeviceOrgRelationDO> findById(String id);

    Optional<DeviceOrgRelationDO> findByUnitCode(String unitCode);

    List<DeviceOrgRelationDO> findByParentId(Long parentId);

    boolean existsByUnitCode(String unitCode, String excludeId);

    PageResult<DeviceOrgRelationDO> selectPage(DeviceOrgRelationPageQuery query);

    void insert(DeviceOrgRelationDO entity);

    void update(DeviceOrgRelationDO entity);

    boolean deleteById(String id);

    List<DeviceOrgRelationDO> listByIds(List<Long> ids);

    List<DeviceOrgRelationDO> findAllActive();
}

