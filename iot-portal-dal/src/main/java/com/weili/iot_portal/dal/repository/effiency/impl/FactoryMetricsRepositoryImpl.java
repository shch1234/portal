package com.weili.iot_portal.dal.repository.effiency.impl;

import com.weili.iot_portal.dal.dataobject.effiency.FactoryMetricsSummaryDO;
import com.weili.iot_portal.dal.repository.effiency.FactoryMetricsRepository;
import com.weili.iot_portal.dal.mapper.effiency.FactoryMetricsSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工厂级效率指标仓储实现
 */
@Repository
@RequiredArgsConstructor
public class FactoryMetricsRepositoryImpl implements FactoryMetricsRepository {

    private final FactoryMetricsSummaryMapper factoryMetricsSummaryMapper;

    @Override
    public Optional<FactoryMetricsSummaryDO> findByShift(String factoryId, String shiftDate, String shiftCode) {
        FactoryMetricsSummaryDO result = factoryMetricsSummaryMapper.selectFactoryMetrics(factoryId, shiftDate, shiftCode);
        return Optional.ofNullable(result);
    }

    @Override
    public List<FactoryMetricsSummaryDO> findHistory(String factoryId, String startDate, String endDate) {
        return factoryMetricsSummaryMapper.selectFactoryMetricsHistory(factoryId, startDate, endDate);
    }
}

