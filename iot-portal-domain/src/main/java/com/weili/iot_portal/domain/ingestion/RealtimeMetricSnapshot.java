package com.weili.iot_portal.domain.ingestion;

import java.math.BigDecimal;

/**
 * @author luying
 * @className RealtimeMetricSnapshot
 * @description
 * @date 2025-12-25 08:56
 **/
public class RealtimeMetricSnapshot {

    private final BigDecimal uptimeRate;
    private final BigDecimal performanceRate;
    private final BigDecimal availabilityRate;
    private final BigDecimal faultRate;
    private final BigDecimal oee;
    private final long updatedAtSec;

    public RealtimeMetricSnapshot(BigDecimal uptimeRate,
                                  BigDecimal performanceRate,
                                  BigDecimal availabilityRate,
                                  BigDecimal faultRate,
                                  BigDecimal oee,
                                  long updatedAtSec) {
        this.uptimeRate = uptimeRate;
        this.performanceRate = performanceRate;
        this.availabilityRate = availabilityRate;
        this.faultRate = faultRate;
        this.oee = oee;
        this.updatedAtSec = updatedAtSec;
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

    public BigDecimal getOee() {
        return oee;
    }

    public long getUpdatedAtSec() {
        return updatedAtSec;
    }

    public static RealtimeMetricSnapshot empty() {
        return new RealtimeMetricSnapshot(BigDecimal.ZERO,BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0);
    }
}
