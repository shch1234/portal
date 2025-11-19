package com.weili.iot_portal.business.efficiency_mgmt.dal.repository.impl;

import com.weili.iot_portal.business.efficiency_mgmt.dal.dataobject.FactoryMetricsShiftDO;
import com.weili.iot_portal.business.efficiency_mgmt.dal.mapper.FactoryMetricsMapper;
import com.weili.iot_portal.business.efficiency_mgmt.dal.repository.FactoryMetricsRepository;
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
    public Optional<FactoryMetricsShiftDO> findByShift(String tenantId, String factoryId, String shiftDate, String shiftCode) {
        FactoryMetricsShiftDO result = factoryMetricsMapper.selectFactoryMetrics(tenantId, factoryId, shiftDate, shiftCode);
        return Optional.ofNullable(result);
    }

    @Override
    public List<FactoryMetricsShiftDO> findHistory(String tenantId, String factoryId, String startDate, String endDate) {
        return factoryMetricsMapper.selectFactoryMetricsHistory(tenantId, factoryId, startDate, endDate);
    }
}

