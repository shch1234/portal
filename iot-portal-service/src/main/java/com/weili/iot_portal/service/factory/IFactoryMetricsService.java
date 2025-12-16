package com.weili.iot_portal.service.factory;

/**
 * 工厂级指标计算/汇总服务
 * - 实时聚合：基于设备实时指标按工厂加权
 * - 班次汇总：基于设备班次指标汇总按工厂加权
 */
public interface IFactoryMetricsService {

    class BatchProcessResult {
        private final int successCount;
        private final int skipCount;
        private final int errorCount;
        private final boolean completed;

        private BatchProcessResult(int successCount, int skipCount, int errorCount, boolean completed) {
            this.successCount = successCount;
            this.skipCount = skipCount;
            this.errorCount = errorCount;
            this.completed = completed;
        }

        public static BatchProcessResult completed(int successCount, int skipCount, int errorCount) {
            return new BatchProcessResult(successCount, skipCount, errorCount, true);
        }

        public static BatchProcessResult incomplete(int successCount, int skipCount, int errorCount) {
            return new BatchProcessResult(successCount, skipCount, errorCount, false);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getSkipCount() {
            return skipCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public boolean isCompleted() {
            return completed;
        }
    }

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

