package com.weili.iot_portal.business.efficiency_mgmt.dal.repository.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.efficiency_mgmt.dal.dataobject.DeviceEfficiencyMetricDO;
import com.weili.iot_portal.business.efficiency_mgmt.dal.mapper.EfficiencyMetricMapper;
import com.weili.iot_portal.business.efficiency_mgmt.dal.repository.EfficiencyMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 效率指标仓储实现
 */
@Repository
@RequiredArgsConstructor
public class EfficiencyMetricRepositoryImpl implements EfficiencyMetricRepository {

    private final EfficiencyMetricMapper efficiencyMetricMapper;

    @Override
    public long countDeviceMetrics(
            String tenantId,
            String factoryId,
            String workshopId,
            String metricCode,
            String shiftDate,
            String shiftCode) {
        return efficiencyMetricMapper.countDeviceMetrics(
                tenantId, factoryId, workshopId, metricCode, shiftDate, shiftCode);
    }

    @Override
    public PageResult<DeviceEfficiencyMetricDO> selectDeviceMetrics(
            String tenantId,
            String factoryId,
            String workshopId,
            String metricCode,
            String shiftDate,
            String shiftCode,
            String sortBy,
            String sortDirection,
            int pageNo,
            int pageSize) {
        // 计算总数
        long total = countDeviceMetrics(
                tenantId, factoryId, workshopId, metricCode, shiftDate, shiftCode);

        // 分页查询
        int offset = (pageNo - 1) * pageSize;
        List<DeviceEfficiencyMetricDO> records = efficiencyMetricMapper.selectDeviceMetrics(
                tenantId, factoryId, workshopId, metricCode, shiftDate, shiftCode,
                sortBy, sortDirection, offset, pageSize);

        return PageResult.of(records, total, pageNo, pageSize);
    }
}

