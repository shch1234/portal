package com.weili.iot_portal.domain.ingestion;

/**
 * @author luying
 * @className BatchProcessResult
 * @description 批量处理结果
 * @date 2025-12-25 08:47
 **/
public class BatchProcessResult {
    private final int successCount;
    private final int skipCount;
    private final int errorCount;
    private final boolean completed;

    BatchProcessResult(int successCount, int skipCount, int errorCount, boolean completed) {
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
