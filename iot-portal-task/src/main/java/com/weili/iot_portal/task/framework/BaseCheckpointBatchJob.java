package com.weili.iot_portal.task.framework;

import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 支持检查点的分批处理任务基类
 * 
 * 功能：
 * - 继承 BaseBatchJob 的所有功能
 * - 支持检查点机制（失败后可恢复）
 * - 自动跳过已处理的数据
 * - 自动保存和清除检查点
 * 
 * 使用方式：
 * 1. 继承此类
 * 2. 实现 buildCheckpointKey() 方法构建检查点Key
 * 3. 实现 getItemId() 方法返回数据项唯一标识
 * 4. 实现 queryData() 和 processItem() 方法
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseCheckpointBatchJob<T> extends BaseBatchJob<T> {
    
    protected final CheckpointManager checkpointManager;
    
    /**
     * 构建检查点Key（由子类实现）
     * 检查点Key应该唯一标识本次处理任务，例如：tenantId:factoryId:statisticsTimeSeconds
     * 
     * @return 检查点Key
     */
    protected abstract String buildCheckpointKey();
    
    /**
     * 获取数据项的唯一标识（用于检查点）
     * 必须与 getItemId() 返回的值一致
     * 
     * @param item 数据项
     * @return 唯一标识
     */
    protected abstract String getItemId(T item);
    
    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        // 加载检查点
        String checkpointKey = buildCheckpointKey();
        CheckpointManager.CheckpointData checkpoint = 
                checkpointManager.loadCheckpoint(getJobName(), checkpointKey);
        
        Set<String> processedIds = checkpoint != null && checkpoint.getProcessedItems() != null
                ? new HashSet<>(checkpoint.getProcessedItems())
                : new HashSet<>();
        
        // 查询数据
        List<T> allData = queryData();
        
        if (allData == null || allData.isEmpty()) {
            // 如果所有数据已处理，清除检查点
            if (!processedIds.isEmpty()) {
                checkpointManager.clearCheckpoint(getJobName(), checkpointKey);
            }
            return JobExecutionResult.empty();
        }
        
        // 过滤已处理的数据
        List<T> remainingData = allData.stream()
                .filter(item -> !processedIds.contains(getItemId(item)))
                .collect(Collectors.toList());
        
        if (remainingData.isEmpty()) {
            XxlJobHelper.log("所有数据已处理，清除检查点");
            checkpointManager.clearCheckpoint(getJobName(), checkpointKey);
            return JobExecutionResult.empty();
        }
        
        if (checkpoint != null) {
            XxlJobHelper.log("从检查点恢复: 已处理={}, 剩余={}", 
                    processedIds.size(), remainingData.size());
        }
        
        // 处理剩余数据
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        List<String> newProcessedIds = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long timeoutMs = getTimeoutMs();
        
        for (int i = 0; i < remainingData.size(); i += batchSize) {
            // 检查超时
            if (!shouldContinue(startTime, timeoutMs)) {
                XxlJobHelper.log("处理超时，保存检查点: 已处理={}, 剩余={}", 
                        processedIds.size(), remainingData.size() - processedIds.size());
                // 保存检查点
                if (!newProcessedIds.isEmpty() || !processedIds.isEmpty()) {
                    List<String> allProcessedIds = new ArrayList<>(processedIds);
                    checkpointManager.saveCheckpoint(getJobName(), checkpointKey, allProcessedIds);
                }
                break;
            }
            
            int endIndex = Math.min(i + batchSize, remainingData.size());
            List<T> batch = remainingData.subList(i, endIndex);
            
            for (T item : batch) {
                try {
                    ItemProcessResult result = processItem(item);
                    String itemId = getItemId(item);
                    
                    if (result.isSuccess()) {
                        successCount++;
                        newProcessedIds.add(itemId);
                        processedIds.add(itemId);
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
                }
            }
            
            // 每批处理完后更新检查点
            if (!newProcessedIds.isEmpty()) {
                List<String> allProcessedIds = new ArrayList<>(processedIds);
                checkpointManager.saveCheckpoint(getJobName(), checkpointKey, allProcessedIds);
                newProcessedIds.clear();
            }
        }
        
        // 如果所有数据都处理完成，清除检查点
        if (processedIds.size() >= allData.size()) {
            checkpointManager.clearCheckpoint(getJobName(), checkpointKey);
        } else if (!processedIds.isEmpty()) {
            // 还有未处理的数据，保存检查点
            List<String> allProcessedIds = new ArrayList<>(processedIds);
            checkpointManager.saveCheckpoint(getJobName(), checkpointKey, allProcessedIds);
        }
        
        return JobExecutionResult.of(successCount, skipCount, errorCount);
    }
}

