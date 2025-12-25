package com.weili.iot_portal.service.model;

/**
 * @author luying
 * @className MetricsCalculationResult
 * @description
 * @date 2025-12-25 08:56
 **/
public class MetricsCalculationResult {

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
