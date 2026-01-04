package com.weili.iot_portal.domain.ingestion;

import java.math.BigDecimal;

/**
 * 工厂实时指标快照
 * <p>
 * 用于封装工厂级别的实时OEE相关指标
 * </p>
 *
 * @author system
 */
public class FactoryRealtimeMetricSnapshot {

    /**
     * OEE指标
     */
    private final BigDecimal oee;

    /**
     * 时间开动率
     */
    private final BigDecimal uptimeRate;

    /**
     * 性能率
     */
    private final BigDecimal performanceRate;

    /**
     * 可用率
     */
    private final BigDecimal availabilityRate;

    /**
     * 故障率
     */
    private final BigDecimal faultRate;

    /**
     * 权重总和（计划运行时长总和，秒）
     */
    private final long sumWeight;

    /**
     * 有效设备数
     */
    private final int validDevices;

    /**
     * 总设备数
     */
    private final int totalDevices;

    /**
     * 数据完整度
     */
    private final BigDecimal dataCompleteness;

    /**
     * 更新时间（秒）
     */
    private final long updatedAtSec;

    public FactoryRealtimeMetricSnapshot(
            BigDecimal oee,
            BigDecimal uptimeRate,
            BigDecimal performanceRate,
            BigDecimal availabilityRate,
            BigDecimal faultRate,
            long sumWeight,
            int validDevices,
            int totalDevices,
            BigDecimal dataCompleteness,
            long updatedAtSec) {
        this.oee = oee;
        this.uptimeRate = uptimeRate;
        this.performanceRate = performanceRate;
        this.availabilityRate = availabilityRate;
        this.faultRate = faultRate;
        this.sumWeight = sumWeight;
        this.validDevices = validDevices;
        this.totalDevices = totalDevices;
        this.dataCompleteness = dataCompleteness;
        this.updatedAtSec = updatedAtSec;
    }

    public BigDecimal getOee() {
        return oee;
    }

    public BigDecimal getUptimeRate() {
        return uptimeRate;
    }

    public BigDecimal getPerformanceRate() {
        return performanceRate;
    }

    public BigDecimal getAvailabilityRate() {
        return availabilityRate;
    }

    public BigDecimal getFaultRate() {
        return faultRate;
    }

    public long getSumWeight() {
        return sumWeight;
    }

    public int getValidDevices() {
        return validDevices;
    }

    public int getTotalDevices() {
        return totalDevices;
    }

    public BigDecimal getDataCompleteness() {
        return dataCompleteness;
    }

    public long getUpdatedAtSec() {
        return updatedAtSec;
    }
}

