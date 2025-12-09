package com.weili.iot_portal.task.framework;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务基类
 * 提供统一的异常处理、日志记录、统计收集等功能
 * 
 * 使用方式：
 * 1. 继承此类
 * 2. 实现 getJobName() 方法返回任务名称
 * 3. 实现 executeInternal() 方法实现业务逻辑
 * 4. 在方法上添加 @XxlJob 注解（由子类实现）
 */
@Slf4j
public abstract class BaseScheduledJob {
    
    /**
     * 执行任务逻辑（由子类实现）
     * 
     * @return 任务执行结果
     * @throws Exception 执行异常
     */
    protected abstract JobExecutionResult executeInternal() throws Exception;
    
    /**
     * 任务名称（用于日志）
     * 
     * @return 任务名称
     */
    protected abstract String getJobName();
    
    /**
     * 执行入口（XXL-Job调用）
     * 子类需要在方法上添加 @XxlJob 注解
     */
    public void execute() throws Exception {
        long startTime = System.currentTimeMillis();
        String jobName = getJobName();
        
        XxlJobHelper.log("开始执行任务: {}", jobName);
        log.info("开始执行任务: {}", jobName);
        
        try {
            JobExecutionResult result = executeInternal();
            long cost = System.currentTimeMillis() - startTime;
            
            // 统一输出统计信息
            outputStatistics(jobName, result, cost);
            
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            handleException(jobName, e, cost);
            throw e; // 让XXL-Job处理重试
        }
    }
    
    /**
     * 输出统计信息
     */
    protected void outputStatistics(String jobName, JobExecutionResult result, long cost) {
        if (result.getMessage() != null) {
            XxlJobHelper.log("任务完成: {}, 成功={}, 跳过={}, 失败={}, 耗时={}ms, 消息={}", 
                    jobName, result.getSuccessCount(), result.getSkipCount(), 
                    result.getErrorCount(), cost, result.getMessage());
            log.info("任务完成: {}, 成功={}, 跳过={}, 失败={}, 耗时={}ms, 消息={}", 
                    jobName, result.getSuccessCount(), result.getSkipCount(), 
                    result.getErrorCount(), cost, result.getMessage());
        } else {
            XxlJobHelper.log("任务完成: {}, 成功={}, 跳过={}, 失败={}, 耗时={}ms", 
                    jobName, result.getSuccessCount(), result.getSkipCount(), 
                    result.getErrorCount(), cost);
            log.info("任务完成: {}, 成功={}, 跳过={}, 失败={}, 耗时={}ms", 
                    jobName, result.getSuccessCount(), result.getSkipCount(), 
                    result.getErrorCount(), cost);
        }
    }
    
    /**
     * 处理异常
     */
    protected void handleException(String jobName, Exception e, long cost) {
        log.error("任务执行失败: {}, 耗时={}ms", jobName, cost, e);
        XxlJobHelper.log("任务执行失败: {}, error={}", jobName, e.getMessage());
    }
}

