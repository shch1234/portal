package com.weili.iot_portal.task.framework;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行结果
 * 用于统一返回任务执行统计信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionResult {
    /**
     * 成功数量
     */
    private int successCount;
    
    /**
     * 跳过数量
     */
    private int skipCount;
    
    /**
     * 失败数量
     */
    private int errorCount;
    
    /**
     * 附加消息
     */
    private String message;
    
    /**
     * 创建空结果（未找到待处理数据）
     */
    public static JobExecutionResult empty() {
        return new JobExecutionResult(0, 0, 0, "未找到待处理数据");
    }
    
    /**
     * 创建结果（无消息）
     */
    public static JobExecutionResult of(int successCount, int skipCount, int errorCount) {
        return new JobExecutionResult(successCount, skipCount, errorCount, null);
    }
    
    /**
     * 创建结果（带消息）
     */
    public static JobExecutionResult of(int successCount, int skipCount, int errorCount, String message) {
        return new JobExecutionResult(successCount, skipCount, errorCount, message);
    }
    
    /**
     * 获取总处理数量
     */
    public int getTotalCount() {
        return successCount + skipCount + errorCount;
    }
    
    /**
     * 是否全部成功
     */
    public boolean isAllSuccess() {
        return errorCount == 0 && skipCount == 0 && successCount > 0;
    }
}

