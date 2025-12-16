package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;

import java.util.List;

/**
 * 设备产量汇总服务接口
 * 负责将 device_production_record 按班次汇总到 device_production_summary
 */
public interface IDeviceProductionSummaryService {

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
     * 处理所有设备（带检查点）
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理指定工厂的设备（带检查点）
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            String factoryId,
            List<DeviceInfoDO> devices,
            long statisticsTimeSeconds,
            int batchSize,
            long timeoutMillis);
}

