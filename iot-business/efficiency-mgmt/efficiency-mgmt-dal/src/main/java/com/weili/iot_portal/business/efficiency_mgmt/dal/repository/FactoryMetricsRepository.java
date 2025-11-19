package com.weili.iot_portal.business.efficiency_mgmt.dal.repository;

import com.weili.iot_portal.business.efficiency_mgmt.dal.dataobject.FactoryMetricsShiftDO;

import java.util.List;
import java.util.Optional;

/**
 * 工厂级效率指标仓储接口
 */
public interface FactoryMetricsRepository {

    /**
     * 查询工厂级效率指标（按班次）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param shiftDate 班次日期
     * @param shiftCode 班次编码
     * @return 工厂级效率指标
     */
    Optional<FactoryMetricsShiftDO> findByShift(String tenantId, String factoryId, String shiftDate, String shiftCode);

    /**
     * 查询工厂级效率指标历史趋势（过去N天）
     * 
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param startDate 开始日期（包含）
     * @param endDate 结束日期（包含）
     * @return 工厂级效率指标列表
     */
    List<FactoryMetricsShiftDO> findHistory(String tenantId, String factoryId, String startDate, String endDate);
}

