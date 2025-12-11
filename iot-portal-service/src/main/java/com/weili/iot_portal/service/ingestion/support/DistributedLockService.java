package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务
 * 使用 RedisClient 实现分布式锁操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedisClient redisClient;
    private final RealTimeCacheService realTimeCacheService;

    /**
     * 尝试获取分布式锁
     * 
     * @param lockKey 锁的键
     * @param timeoutSeconds 锁的超时时间（秒）
     * @return true 表示成功获取锁，false 表示锁已被占用，null 表示操作异常
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Boolean tryLock(String lockKey, long timeoutSeconds) {
        try {
            boolean result = redisClient.tryLock(lockKey, timeoutSeconds, TimeUnit.SECONDS);
            log.debug("[DistributedLock] 尝试获取锁: lockKey={}, result={}, timeout={}秒", 
                lockKey, result, timeoutSeconds);
            return result;
        } catch (Exception e) {
            log.error("[DistributedLock] 获取锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 释放分布式锁
     * 
     * @param lockKey 锁的键
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void releaseLock(String lockKey) {
        try {
            redisClient.releaseLock(lockKey);
            log.debug("[DistributedLock] 释放锁: lockKey={}", lockKey);
        } catch (Exception e) {
            log.error("[DistributedLock] 释放锁异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
        }
    }

    /**
     * 检查锁是否存在
     * 
     * @param lockKey 锁的键
     * @return true 表示锁存在，false 表示锁不存在，null 表示操作异常
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Boolean isLocked(String lockKey) {
        try {
            // 通过检查 key 是否存在来判断锁是否被占用
            String value = redisClient.get(lockKey);
            boolean exists = value != null;
            log.debug("[DistributedLock] 检查锁状态: lockKey={}, exists={}", lockKey, exists);
            return exists;
        } catch (Exception e) {
            log.error("[DistributedLock] 检查锁状态异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取锁的剩余过期时间
     * 
     * @param lockKey 锁的键
     * @return 剩余过期时间（秒），-1 表示锁不存在，-2 表示锁没有设置过期时间，null 表示操作异常
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long getLockTtl(String lockKey) {
        try {
            // 使用 RealTimeCacheService 获取 TTL
            Long ttl = realTimeCacheService.getTtlSeconds(lockKey);
            log.debug("[DistributedLock] 获取锁TTL: lockKey={}, ttl={}秒", lockKey, ttl);
            return ttl;
        } catch (Exception e) {
            log.error("[DistributedLock] 获取锁TTL异常: lockKey={}, error={}", lockKey, e.getMessage(), e);
            return null;
        }
    }
}


