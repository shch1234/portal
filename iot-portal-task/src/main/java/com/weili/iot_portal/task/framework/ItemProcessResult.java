package com.weili.iot_portal.task.framework;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 单项处理结果
 * 用于返回单个数据项的处理结果
 */
@Data
@AllArgsConstructor
public class ItemProcessResult {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 是否跳过
     */
    private boolean skipped;
    
    /**
     * 消息（失败原因或跳过原因）
     */
    private String message;
    
    /**
     * 创建成功结果
     */
    public static ItemProcessResult success() {
        return new ItemProcessResult(true, false, null);
    }
    
    /**
     * 创建成功结果（带消息）
     */
    public static ItemProcessResult success(String message) {
        return new ItemProcessResult(true, false, message);
    }
    
    /**
     * 创建跳过结果
     */
    public static ItemProcessResult skipped(String reason) {
        return new ItemProcessResult(false, true, reason);
    }
    
    /**
     * 创建失败结果
     */
    public static ItemProcessResult failed(String reason) {
        return new ItemProcessResult(false, false, reason);
    }
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * 是否跳过
     */
    public boolean isSkipped() {
        return skipped;
    }
    
    /**
     * 是否失败
     */
    public boolean isFailed() {
        return !success && !skipped;
    }
}

