package com.weili.iot_portal.dal.repository.devicemng;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicemng.ProductionCounterDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;

import java.util.Optional;

/**
 * 班次产量仓储
 */
public interface ProductionCounterRepository {

    Optional<ProductionCounterDO> findCurrent(String tenantId, String deviceId, long currentTs);

    PageResult<ProductionCounterDO> selectPage(ProductionCounterPageQuery query);
}


