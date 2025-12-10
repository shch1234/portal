package com.weili.iot_portal.dal.repository.effiency.impl;

import com.weili.iot_portal.dal.dataobject.efficiency.FactoryMetricsShiftDO;
import com.weili.iot_portal.dal.repository.effiency.FactoryMetricsRepository;
import com.weili.iot_portal.dal.mapper.effiency.FactoryMetricsMapper;
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

    private final FactoryMetricsMapper factoryMetricsMapper;

    @Override
    public Optional<FactoryMetricsShiftDO> findByShift(String factoryId, String shiftDate, String shiftCode) {
        FactoryMetricsShiftDO result = factoryMetricsMapper.selectFactoryMetrics(factoryId, shiftDate, shiftCode);
        return Optional.ofNullable(result);
    }

    @Override
    public List<FactoryMetricsShiftDO> findHistory(String factoryId, String startDate, String endDate) {
        return factoryMetricsMapper.selectFactoryMetricsHistory(factoryId, startDate, endDate);
    }
}

