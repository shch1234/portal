package com.weili.iot_portal.service.device;

/**
 * 设备班次指标汇总服务
 * 依赖已完成的 device_state_summary，计算班次级指标并写入 device_metrics_summary
 */
public interface IDeviceMetricsSummaryService {

    /**
     * 批量处理结果
     */
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
     * 批量处理所有设备的班次指标汇总（带检查点机制）
     *
     * @param statPointSeconds 统计时间点（秒），仅处理 shift_end_ts <= statPoint 的已完成汇总
     * @param batchSize        每批处理设备数
     * @param timeoutMillis    超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statPointSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 批量处理工厂设备的班次指标汇总（带检查点机制）
     *
     * @param factoryId        工厂ID
     * @param statPointSeconds 统计时间点（秒）
     * @param batchSize        批大小
     * @param timeoutMillis    超时时间
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            String factoryId,
            java.util.List<com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO> devices,
            long statPointSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 向后兼容旧接口（不带检查点），内部可委托带检查点的实现
     */
    default BatchProcessResult processAllDevices(long statPointSeconds) {
        return processAllDevicesWithCheckpoint(statPointSeconds, 50, 15 * 60 * 1000);
    }
}

