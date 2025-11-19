package com.weili.iot_portal.business.efficiency_mgmt.dal.repository;

import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.efficiency_mgmt.dal.dataobject.DeviceEfficiencyMetricDO;

import java.util.List;

/**
 * 效率指标仓储接口
 */
public interface EfficiencyMetricRepository {

    /**
     * 查询效率指标列表总数
     */
    long countDeviceMetrics(
            String tenantId,
            String factoryId,
            String workshopId,
            String metricCode,
            String shiftDate,
            String shiftCode);

    /**
     * 查询效率指标列表（分页、排序）
     */
    PageResult<DeviceEfficiencyMetricDO> selectDeviceMetrics(
            String tenantId,
            String factoryId,
            String workshopId,
            String metricCode,
            String shiftDate,
            String shiftCode,
            String sortBy,
            String sortDirection,
            int pageNo,
            int pageSize);
}

