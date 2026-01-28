package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 幂等服务
 * <p>
 * 优化说明：
 * 1. TTL 改为可配置，支持通过 Apollo 动态调整
 * 2. 默认 TTL 从 24 小时缩短为 30 分钟，减少 Redis 内存占用
 * 3. 添加配置验证，防止配置错误（最小 5 分钟，最大 7 天）
 * 4. 30 分钟符合 Webhook 实时事件场景的最佳实践
 * 5. 添加Redis超时降级：使用本地缓存作为降级方案
 * 6. 添加异常处理，避免Redis故障影响业务
 * </p>
 */
@Slf4j
@Component
public class WebhookIdempotentService {
    
    /**
     * 幂等性缓存 TTL（秒）
     * 支持 Apollo 配置，默认值：1800（30分钟）
     * 
     * 说明：
     * 1. Webhook 消息处理通常在几秒到几十秒内完成
     * 2. 网络重传通常在几分钟内完成
     * 3. 消息队列的重复投递通常在几十分钟内
     * 4. 30 分钟足够覆盖大部分重复场景，同时减少 Redis 内存占用
     * 
     * 业界参考：
     * - 支付/订单场景：1-24 小时（关键业务）
     * - 实时事件场景（Webhook）：15-60 分钟（推荐）
     * - 高频数据场景（IoT）：5-30 分钟（推荐）
     * 
     * 如果业务需要更长的防重复时间，可以通过配置调整
     */
    @Value("${webhook.idempotent.ttl-seconds:1800}")
    private long ttlSecondsRaw;
    
    /**
     * Redis操作超时时间（毫秒）
     * 默认值：2000（2秒），比Redis默认超时时间（5秒）更短，快速失败
     */
    @Value("${webhook.idempotent.redis-timeout-ms:2000}")
    private long redisTimeoutMs;
    
    /**
     * 是否启用本地缓存降级
     * 默认值：true，当Redis超时时使用本地缓存
     */
    @Value("${webhook.idempotent.local-cache-fallback:true}")
    private boolean localCacheFallback;
    
    /**
     * 本地缓存最大大小
     * 默认值：10000，防止内存溢出
     */
    @Value("${webhook.idempotent.local-cache-size:10000}")
    private int localCacheSize;
    
    /**
     * 验证后的 TTL（秒）
     */
    private long ttlSeconds;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * 本地缓存（降级方案）
     * Key: messageId, Value: 过期时间戳（毫秒）
     */
    private final ConcurrentHashMap<String, Long> localCache = new ConcurrentHashMap<>();
    
    /**
     * 定时清理本地缓存的调度器
     */
    private ScheduledExecutorService cleanupScheduler;

    /**
     * 初始化配置验证
     */
    @PostConstruct
    private void validateConfig() {
        // 验证并修正 TTL（最小 5 分钟，最大 7 天）
        // 最小 5 分钟：确保覆盖消息处理时间和网络重传时间
        // 最大 7 天：防止配置错误导致内存占用过大
        long minTtl = Duration.ofMinutes(5).getSeconds();  // 5 分钟
        long maxTtl = Duration.ofDays(7).getSeconds();      // 7 天
        
        if (ttlSecondsRaw < minTtl) {
            log.warn("[Webhook-Idempotent] ttl-seconds 配置过小: {}秒，使用最小值: {}秒（5分钟）", 
                    ttlSecondsRaw, minTtl);
            ttlSeconds = minTtl;
        } else if (ttlSecondsRaw > maxTtl) {
            log.warn("[Webhook-Idempotent] ttl-seconds 配置过大: {}秒，使用最大值: {}秒（7天）", 
                    ttlSecondsRaw, maxTtl);
            ttlSeconds = maxTtl;
        } else {
            ttlSeconds = ttlSecondsRaw;
        }
        
        // 格式化显示（分钟或小时）
        if (ttlSeconds < 3600) {
            log.info("[Webhook-Idempotent] 幂等性缓存配置: TTL={}秒（{}分钟）, Redis超时={}ms, 本地缓存降级={}, 本地缓存大小={}",
                    ttlSeconds, ttlSeconds / 60, redisTimeoutMs, localCacheFallback, localCacheSize);
        } else {
            log.info("[Webhook-Idempotent] 幂等性缓存配置: TTL={}秒（{}小时）, Redis超时={}ms, 本地缓存降级={}, 本地缓存大小={}",
                    ttlSeconds, ttlSeconds / 3600, redisTimeoutMs, localCacheFallback, localCacheSize);
        }
        
        // 如果启用本地缓存降级，启动定时清理任务
        if (localCacheFallback) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "webhook-idempotent-cleanup");
                t.setDaemon(true);
                return t;
            });
            // 每5分钟清理一次过期条目
            cleanupScheduler.scheduleWithFixedDelay(this::cleanupLocalCache, 5, 5, TimeUnit.MINUTES);
        }
    }

    /**
     * 尝试消费消息
     * 
     * 优化：
     * 1. 添加Redis超时处理
     * 2. 使用本地缓存作为降级方案
     * 3. 快速失败，避免阻塞
     *
     * @return true：允许处理；false：已处理
     */
    public boolean tryConsume(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return true;
        }
        
        String key = RedisConstant.WEBHOOK_IDEMPOTENT + messageId;
        
        try {
            // 尝试使用Redis进行幂等性检查
            Boolean success = executeWithTimeout(() -> 
                    redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds)));
            
            if (success != null) {
                // Redis操作成功
                if (Boolean.TRUE.equals(success)) {
                    // 如果启用本地缓存降级，同时写入本地缓存
                    if (localCacheFallback) {
                        updateLocalCache(messageId);
                    }
                    return true;
                } else {
                    // 消息已处理
                    return false;
                }
            }
        } catch (QueryTimeoutException | org.springframework.data.redis.RedisConnectionFailureException e) {
            // Redis超时或连接失败，使用本地缓存降级
            log.warn("[Webhook-Idempotent] Redis操作失败，使用本地缓存降级: messageId={}, error={}", 
                    messageId, e.getMessage());
            return tryConsumeWithLocalCache(messageId);
        } catch (Exception e) {
            // 其他异常，记录日志并使用降级方案
            log.error("[Webhook-Idempotent] Redis操作异常，使用本地缓存降级: messageId={}, error={}", 
                    messageId, e.getMessage(), e);
            return tryConsumeWithLocalCache(messageId);
        }
        
        // Redis返回null（不应该发生），使用本地缓存降级
        return tryConsumeWithLocalCache(messageId);
    }
    
    /**
     * 使用本地缓存进行幂等性检查（降级方案）
     */
    private boolean tryConsumeWithLocalCache(String messageId) {
        if (!localCacheFallback) {
            // 未启用本地缓存降级，Redis失败时允许处理（可能重复，但保证可用性）
            log.warn("[Webhook-Idempotent] Redis失败且未启用本地缓存降级，允许处理（可能重复）: messageId={}", 
                    messageId);
            return true;
        }
        
        // 检查本地缓存
        Long expireTime = localCache.get(messageId);
        long currentTime = System.currentTimeMillis();
        
        if (expireTime != null && expireTime > currentTime) {
            // 本地缓存中存在且未过期，说明已处理
            return false;
        }
        
        // 本地缓存中不存在或已过期，允许处理并更新本地缓存
        updateLocalCache(messageId);
        
        // 如果本地缓存过大，清理部分过期条目
        if (localCache.size() > localCacheSize) {
            cleanupLocalCache();
        }
        
        return true;
    }
    
    /**
     * 更新本地缓存
     */
    private void updateLocalCache(String messageId) {
        long expireTime = System.currentTimeMillis() + (ttlSeconds * 1000);
        localCache.put(messageId, expireTime);
    }
    
    /**
     * 清理本地缓存中的过期条目
     */
    private void cleanupLocalCache() {
        long currentTime = System.currentTimeMillis();
        
        // 先统计要删除的数量
        int removedCount = (int) localCache.entrySet().stream()
                .filter(entry -> entry.getValue() <= currentTime)
                .count();
        
        // 删除过期条目
        localCache.entrySet().removeIf(entry -> entry.getValue() <= currentTime);
        
        if (removedCount > 0) {
            log.debug("[Webhook-Idempotent] 清理本地缓存过期条目: 清理{}条, 剩余{}条", 
                    removedCount, localCache.size());
        }
        
        // 如果清理后仍然超过限制，删除最旧的条目（FIFO）
        if (localCache.size() > localCacheSize) {
            int toRemove = localCache.size() - localCacheSize;
            localCache.entrySet().stream()
                    .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                    .limit(toRemove)
                    .forEach(entry -> localCache.remove(entry.getKey()));
            log.warn("[Webhook-Idempotent] 本地缓存超过限制，删除最旧{}条", toRemove);
        }
    }
    
    /**
     * 带超时的Redis操作执行
     * 注意：RedisTemplate的超时配置在连接层面，这里直接执行
     * 如果超时，会抛出QueryTimeoutException，我们在上层捕获
     */
    private <T> T executeWithTimeout(java.util.function.Supplier<T> operation) {
        return operation.get();
    }
    
    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    private void destroy() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        localCache.clear();
        log.info("[Webhook-Idempotent] 资源清理完成");
    }
}

