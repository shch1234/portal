package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;

import java.util.Optional;
import java.time.LocalDate;

/**
 * 班次产量仓储
 */
public interface DeviceProductionSummaryRepository {

    Optional<DeviceProductionSummaryDO> findCurrent(String deviceId, long currentTs);

    PageResult<DeviceProductionSummaryDO> selectPage(ProductionCounterPageQuery query);

    DeviceProductionSummaryDO findByShift(String deviceId, LocalDate shiftDate, String shiftCode);

    void insert(DeviceProductionSummaryDO entity);

    void update(DeviceProductionSummaryDO entity);
}


