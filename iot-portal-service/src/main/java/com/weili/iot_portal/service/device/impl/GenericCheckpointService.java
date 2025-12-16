package com.weili.iot_portal.service.device.impl;

import com.weili.basic.common.util.JsonUtils;
import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.service.device.ICheckpointService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用检查点服务实现
 * 通过配置区分不同的业务场景
 */
@Slf4j
public class GenericCheckpointService implements ICheckpointService<ICheckpointService.CheckpointData> {

    private final RedisClient redisClient;
    private final String keyPrefix;
    private final long ttlSeconds;

    /**
     * 构造函数
     *
     * @param redisClient Redis客户端
     * @param keyPrefix   Redis Key前缀
     * @param ttlSeconds  检查点TTL（秒）
     */
    public GenericCheckpointService(RedisClient redisClient, String keyPrefix, long ttlSeconds) {
        this.redisClient = redisClient;
        this.keyPrefix = keyPrefix;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public ICheckpointService.CheckpointData loadCheckpoint(String factoryId, long timeSeconds) {
        String key = buildCheckpointKey(factoryId, timeSeconds);
        try {
            String value = redisClient.get(key);
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return JsonUtils.parseObject(value, ICheckpointService.CheckpointData.class);
        } catch (Exception e) {
            log.error("加载检查点失败: key={}", key, e);
            return null;
        }
    }

    @Override
    public void saveCheckpoint(String factoryId, long timeSeconds, List<String> processedDeviceIds) {
        String key = buildCheckpointKey(factoryId, timeSeconds);

        ICheckpointService.CheckpointData checkpoint = new ICheckpointService.CheckpointData();
        checkpoint.setFactoryId(factoryId);
        checkpoint.setTimeSeconds(timeSeconds);
        checkpoint.setProcessedDeviceIds(processedDeviceIds);
        checkpoint.setTotalDeviceCount(processedDeviceIds.size());
        checkpoint.setLastUpdateTime(System.currentTimeMillis() / 1000);

        try {
            redisClient.set(key, JsonUtils.toJsonString(checkpoint),
                    ttlSeconds, TimeUnit.SECONDS);
            log.debug("保存检查点: key={}, processedCount={}", key, processedDeviceIds.size());
        } catch (Exception e) {
            log.error("保存检查点失败: key={}", key, e);
        }
    }

    @Override
    public void clearCheckpoint(String factoryId, long timeSeconds) {
        String key = buildCheckpointKey(factoryId, timeSeconds);
        try {
            redisClient.delete(key);
            log.debug("清除检查点: key={}", key);
        } catch (Exception e) {
            log.error("清除检查点失败: key={}", key, e);
        }
    }

    @Override
    public Set<String> getProcessedDeviceIds(String factoryId, long timeSeconds) {
        ICheckpointService.CheckpointData checkpoint = loadCheckpoint(factoryId, timeSeconds);
        if (checkpoint == null || checkpoint.getProcessedDeviceIds() == null) {
            return new HashSet<>();
        }
        return new HashSet<>(checkpoint.getProcessedDeviceIds());
    }

    /**
     * 构建检查点Key
     */
    private String buildCheckpointKey(String factoryId, long timeSeconds) {
        return String.format("%s%s:%d", keyPrefix, factoryId, timeSeconds);
    }
}

