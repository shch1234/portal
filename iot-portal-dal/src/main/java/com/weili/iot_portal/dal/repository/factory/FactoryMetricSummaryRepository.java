package com.weili.iot_portal.dal.repository.factory;

import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 工厂级指标汇总仓储
 * 对应表：factory_metric_summary
 */
public interface FactoryMetricSummaryRepository {

    FactoryMetricSummaryDO findByShift(String factoryId, LocalDate shiftDate, String shiftCode);

    void insert(FactoryMetricSummaryDO record);

    void update(FactoryMetricSummaryDO record);

    /**
     * 查询待补偿/待重算的记录（可选，按时间范围）
     */
    List<FactoryMetricSummaryDO> findPendingByDays(int days);
}

