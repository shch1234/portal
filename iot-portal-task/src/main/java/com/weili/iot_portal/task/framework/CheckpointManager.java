package com.weili.iot_portal.task.framework;

import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 检查点管理器
 * 提供检查点的保存、加载、清除功能
 * 
 * 使用场景：
 * - 大批量数据处理任务
 * - 需要支持失败恢复的任务
 * - 需要记录处理进度的任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckpointManager {
    
    private final RedisClient redisClient;
    private static final String CHECKPOINT_PREFIX = "job:checkpoint:";
    private static final long DEFAULT_TTL_SECONDS = 24 * 3600; // 24小时
    
    /**
     * 保存检查点
     * 
     * @param jobName 任务名称
     * @param key 检查点Key（由业务逻辑决定）
     * @param processedItems 已处理的数据项ID列表
     */
    public void saveCheckpoint(String jobName, String key, List<String> processedItems) {
        String redisKey = buildKey(jobName, key);
        CheckpointData data = new CheckpointData();
        data.setProcessedItems(processedItems);
        data.setLastUpdateTime(System.currentTimeMillis() / 1000);
        
        try {
            redisClient.set(redisKey, JsonUtils.toJsonString(data), 
                    DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("保存检查点: key={}, processedCount={}", redisKey, processedItems.size());
        } catch (Exception e) {
            log.error("保存检查点失败: key={}", redisKey, e);
        }
    }
    
    /**
     * 加载检查点
     * 
     * @param jobName 任务名称
     * @param key 检查点Key
     * @return 检查点数据，如果不存在返回null
     */
    public CheckpointData loadCheckpoint(String jobName, String key) {
        String redisKey = buildKey(jobName, key);
        try {
            String value = redisClient.get(redisKey);
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return JsonUtils.parseObject(value, CheckpointData.class);
        } catch (Exception e) {
            log.error("加载检查点失败: key={}", redisKey, e);
            return null;
        }
    }
    
    /**
     * 清除检查点
     * 
     * @param jobName 任务名称
     * @param key 检查点Key
     */
    public void clearCheckpoint(String jobName, String key) {
        String redisKey = buildKey(jobName, key);
        try {
            redisClient.delete(redisKey);
            log.debug("清除检查点: key={}", redisKey);
        } catch (Exception e) {
            log.error("清除检查点失败: key={}", redisKey, e);
        }
    }
    
    /**
     * 构建Redis Key
     */
    private String buildKey(String jobName, String key) {
        return CHECKPOINT_PREFIX + jobName + ":" + key;
    }
    
    /**
     * 检查点数据
     */
    @Data
    public static class CheckpointData {
        /**
         * 已处理的数据项ID列表
         */
        private List<String> processedItems;
        
        /**
         * 最后更新时间戳（秒）
         */
        private long lastUpdateTime;
    }
}

