package com.weili.iot_portal.domain.ingestion;

import lombok.Data;

/**
 * @author luying
 * @className ProcessResult
 * @description
 * @date 2025-12-25 08:46
 **/
@Data
public class ProcessResult {

    private boolean processed;
    private String skipReason;
    private int successCount;
    private int skipCount;
    private int errorCount;


    public ProcessResult(boolean processed, String skipReason, int successCount, int skipCount, int errorCount) {
        this.processed = processed;
        this.skipReason = skipReason;
        this.successCount = successCount;
        this.skipCount = skipCount;
        this.errorCount = errorCount;
    }

    public ProcessResult(int successCount, int skipCount, int errorCount) {
        this.successCount = successCount;
        this.skipCount = skipCount;
        this.errorCount = errorCount;
    }

    public ProcessResult(boolean processed, String skipReason) {
        this.processed = processed;
        this.skipReason = skipReason;
    }

    public static ProcessResult empty() {
        return new ProcessResult(false, null, 0, 0, 0);
    }


    public static ProcessResult processed() {
        return new ProcessResult(true, null);
    }

    public static ProcessResult skipped(String reason) {
        return new ProcessResult(false, reason);
    }

    public boolean isProcessed() {
        return processed;
    }

    public String getSkipReason() {
        return skipReason;
    }
}
