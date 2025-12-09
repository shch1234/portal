package com.weili.iot_portal.task.framework;

import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * 分批处理任务基类
 * 适用于需要处理大量数据的任务
 * 
 * 功能：
 * - 自动分批处理
 * - 超时控制
 * - 异常隔离（单个失败不影响其他）
 * - 统一统计收集
 * 
 * 使用方式：
 * 1. 继承此类
 * 2. 实现 queryData() 方法查询待处理数据
 * 3. 实现 processItem() 方法处理单个数据项
 * 4. 实现 getItemId() 方法返回数据项唯一标识
 */
@Slf4j
public abstract class BaseBatchJob<T> extends BaseScheduledJob {
    
    /**
     * 每批处理的数据量（可配置）
     */
    @Value("${job.batch.size:50}")
    protected int batchSize;
    
    /**
     * 查询待处理数据
     * 
     * @return 待处理数据列表
     */
    protected abstract List<T> queryData();
    
    /**
     * 处理单个数据项
     * 
     * @param item 数据项
     * @return 处理结果
     */
    protected abstract ItemProcessResult processItem(T item);
    
    /**
     * 获取数据项的唯一标识（用于日志）
     * 
     * @param item 数据项
     * @return 唯一标识
     */
    protected String getItemId(T item) {
        return String.valueOf(item.hashCode());
    }
    
    /**
     * 是否继续处理（用于超时控制）
     * 
     * @param startTime 开始时间
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否继续
     */
    protected boolean shouldContinue(long startTime, long timeoutMs) {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < timeoutMs;
    }
    
    /**
     * 获取超时时间（毫秒），默认25分钟
     */
    protected long getTimeoutMs() {
        return 25 * 60 * 1000; // 25分钟
    }
    
    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        List<T> dataList = queryData();
        
        if (dataList == null || dataList.isEmpty()) {
            XxlJobHelper.log("未找到待处理数据");
            return JobExecutionResult.empty();
        }
        
        XxlJobHelper.log("待处理数据量: {}", dataList.size());
        
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        long startTime = System.currentTimeMillis();
        long timeoutMs = getTimeoutMs();
        
        // 分批处理
        for (int i = 0; i < dataList.size(); i += batchSize) {
            // 检查超时
            if (!shouldContinue(startTime, timeoutMs)) {
                XxlJobHelper.log("处理超时，已处理: {}/{}, 成功={}, 跳过={}, 失败={}", 
                        i, dataList.size(), successCount, skipCount, errorCount);
                break;
            }
            
            int endIndex = Math.min(i + batchSize, dataList.size());
            List<T> batch = dataList.subList(i, endIndex);
            
            for (T item : batch) {
                try {
                    ItemProcessResult result = processItem(item);
                    if (result.isSuccess()) {
                        successCount++;
                    } else if (result.isSkipped()) {
                        skipCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("处理数据项失败: itemId={}", getItemId(item), e);
                    XxlJobHelper.log("处理数据项失败: itemId={}, error={}", 
                            getItemId(item), e.getMessage());
                    // 继续处理下一个，不中断
                }
            }
        }
        
        return JobExecutionResult.of(successCount, skipCount, errorCount);
    }
}

