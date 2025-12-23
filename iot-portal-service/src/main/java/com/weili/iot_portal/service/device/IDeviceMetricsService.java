package com.weili.iot_portal.service.device;

import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;

import java.util.List;

/**
 * 设备实时指标计算服务接口
 * 负责计算设备的OEE相关指标（时间开动率、性能率、可用率、故障率、OEE等）
 */
public interface IDeviceMetricsService {

    /**
     * 批量处理工厂设备的指标计算（带检查点机制）
     * 包含：检查点管理、分批处理、超时控制、结果统计
     *
     * @param factoryId             工厂ID
     * @param devices               设备列表
     * @param calculationTimeSeconds 计算时间点（秒）
     * @param batchSize             批处理大小
     * @param timeoutMillis         超时时间（毫秒）
     * @return 处理结果
     */
    BatchProcessResult processFactoryDevicesWithCheckpoint(
            Long factoryId,
            List<DeviceInfoDO> devices,
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 处理全部设备的指标计算（带检查点，内部按工厂分组+分批）
     */
    BatchProcessResult processAllDevicesWithCheckpoint(
            long calculationTimeSeconds,
            int batchSize,
            long timeoutMillis);

    /**
     * 批量计算所有设备的实时指标（简化版本，不包含检查点）
     *
     * @return 处理结果，包含成功和失败数量
     */
    MetricsCalculationResult calculateAllDevicesMetrics();

    /**
     * 计算单个设备的实时指标
     *
     * @param device 设备信息
     */
    void calculateDeviceMetrics(DeviceInfoDO device);

    /**
     * 获取设备实时指标快照（从Redis读取）
     *
     * @param factoryId 工厂ID（可为空，用于key维度）
     * @param deviceId  设备ID
     * @return 指标快照，可为空（缓存不存在）
     */
    java.util.Optional<RealtimeMetricSnapshot> getDeviceRealtimeMetrics(Long factoryId, Long deviceId);

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
     * 指标计算结果
     */
    class MetricsCalculationResult {
        private final int successCount;
        private final int errorCount;

        private MetricsCalculationResult(int successCount, int errorCount) {
            this.successCount = successCount;
            this.errorCount = errorCount;
        }

        public static MetricsCalculationResult of(int successCount, int errorCount) {
            return new MetricsCalculationResult(successCount, errorCount);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }

    /**
     * 设备实时指标快照
     */
    class RealtimeMetricSnapshot {
        private final java.math.BigDecimal uptimeRate;
        private final java.math.BigDecimal performanceRate;
        private final java.math.BigDecimal availabilityRate;
        private final java.math.BigDecimal faultRate;
        private final java.math.BigDecimal oee;
        private final long updatedAtSec;

        public RealtimeMetricSnapshot(java.math.BigDecimal uptimeRate,
                                      java.math.BigDecimal performanceRate,
                                      java.math.BigDecimal availabilityRate,
                                      java.math.BigDecimal faultRate,
                                      java.math.BigDecimal oee,
                                      long updatedAtSec) {
            this.uptimeRate = uptimeRate;
            this.performanceRate = performanceRate;
            this.availabilityRate = availabilityRate;
            this.faultRate = faultRate;
            this.oee = oee;
            this.updatedAtSec = updatedAtSec;
        }

        public java.math.BigDecimal getUptimeRate() {
            return uptimeRate;
        }

        public java.math.BigDecimal getPerformanceRate() {
            return performanceRate;
        }

        public java.math.BigDecimal getAvailabilityRate() {
            return availabilityRate;
        }

        public java.math.BigDecimal getFaultRate() {
            return faultRate;
        }

        public java.math.BigDecimal getOee() {
            return oee;
        }

        public long getUpdatedAtSec() {
            return updatedAtSec;
        }

        public static RealtimeMetricSnapshot empty() {
            return new RealtimeMetricSnapshot(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, 0);
        }
    }
}

