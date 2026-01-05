package com.weili.iot_portal.dal.repository.factory;

import com.weili.iot_portal.dal.dataobject.factory.FactoryMetricSummaryDO;

import java.time.LocalDate;
import java.util.List;

/**
 * 工厂级指标汇总仓储
 * 对应表：factory_metric_summary
 */
public interface FactoryMetricSummaryRepository {

    FactoryMetricSummaryDO findByShift(Long factoryId, LocalDate shiftDate, String shiftCode);

    /**
     * 查询工厂在指定时间范围内已确定的班次记录
     *
     * @param factoryId 工厂ID
     * @param startTs   开始时间戳（秒），查询 shift_end_ts >= startTs 的记录，可为null表示不限制
     * @param endTs     结束时间戳（秒），查询 shift_end_ts <= endTs 的记录，可为null表示不限制
     * @return 工厂指标汇总记录列表（按 shift_end_ts 升序）
     */
    List<FactoryMetricSummaryDO> selectFinalizedInRange(Long factoryId, Long startTs, Long endTs);

    /**
     * 查询指定时间范围内已完成的班次记录
     *
     * @param orgFactoryId   工厂id
     * @param startShiftDate 开始班次
     * @param endShiftDate   结束班次
     * @return 班次记录列表（按 shift_end_ts 升序）
     */
    List<FactoryMetricSummaryDO> selectFinalizedInRange(Long orgFactoryId, LocalDate startShiftDate, LocalDate endShiftDate);

    void insert(FactoryMetricSummaryDO record);

    void update(FactoryMetricSummaryDO record);
}

