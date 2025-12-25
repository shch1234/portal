package com.weili.iot_portal.service.model;

/**
 * @author luying
 * @className CompensationResult
 * @description
 * @date 2025-12-25 08:48
 **/
public class CompensationResult {

    private final int successCount;
    private final int skipCount;
    private final int errorCount;

    public CompensationResult(int successCount, int skipCount, int errorCount) {
        this.successCount = successCount;
        this.skipCount = skipCount;
        this.errorCount = errorCount;
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
}
