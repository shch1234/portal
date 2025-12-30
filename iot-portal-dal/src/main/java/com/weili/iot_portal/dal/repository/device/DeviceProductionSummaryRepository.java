package com.weili.iot_portal.dal.repository.device;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 班次产量仓储
 */
public interface DeviceProductionSummaryRepository {

    Optional<DeviceProductionSummaryDO> findCurrent(Long deviceId, long currentTs);

    PageResult<DeviceProductionSummaryDO> selectPage(ProductionCounterPageQuery query);

    DeviceProductionSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode);

    void insert(DeviceProductionSummaryDO entity);

    void update(DeviceProductionSummaryDO entity);

    /**
     * 查询日期范围内的产量汇总数据
     */
    List<DeviceProductionSummaryDO> findByDateRange(Long deviceInfoId, Integer shiftCode, LocalDate startDate, LocalDate endDate);
}


