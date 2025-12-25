package com.weili.iot_portal.service.factory;

import com.weili.iot_portal.service.model.BatchProcessResult;

/**
 * 工厂级指标计算/汇总服务
 * - 实时聚合：基于设备实时指标按工厂加权
 * - 班次汇总：基于设备班次指标汇总按工厂加权
 */
public interface IFactoryMetricsService {
    /**
     * 实时工厂指标计算（带检查点）
     */
    BatchProcessResult processAllFactoriesRealtimeWithCheckpoint(
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 班次级工厂指标汇总（带检查点）
     */
    BatchProcessResult processAllFactoriesShiftSummaryWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);
}

