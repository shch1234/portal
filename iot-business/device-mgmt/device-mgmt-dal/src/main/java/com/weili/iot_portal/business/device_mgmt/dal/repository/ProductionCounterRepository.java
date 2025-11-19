package com.weili.iot_portal.business.device_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ProductionCounterDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.ProductionCounterPageQuery;

import java.util.Optional;

/**
 * 班次产量仓储
 */
public interface ProductionCounterRepository {

    Optional<ProductionCounterDO> findCurrent(String tenantId, String deviceId, long currentTs);

    PageResult<ProductionCounterDO> selectPage(ProductionCounterPageQuery query);
}


