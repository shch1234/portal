package com.weili.iot_portal.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 实时缓存写入工具类
 * <p>
 * 提供统一的Redis写入逻辑，包括错误处理、监控等
 * </p>
 * <p>
 * 功能：
 * 1. 写入Hash数据（带采样和时间偏移）
 * 2. 统一错误处理
 * 3. 异步写入（可选，提高主流程吞吐量）
 * 4. 统一监控指标（预留接口）
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeCacheWriter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 是否启用异步写入
     * Apollo配置：rt.cache.async-write-enabled
     * 默认值：false（同步写入）
     */
    @Value("${rt.cache.async-write-enabled:true}")
    private boolean asyncWriteEnabled;

    /**
     * 异步写入线程数
     * Apollo配置：rt.cache.async-write-threads
     * 默认值：5
     */
    @Value("${rt.cache.async-write-threads:5}")
    private int asyncWriteThreads;

    /**
     * 异步写入队列大小
     * Apollo配置：rt.cache.async-write-queue-size
     * 默认值：1000
     */
    @Value("${rt.cache.async-write-queue-size:1000}")
    private int asyncWriteQueueSize;

    /**
     * 异步写入线程池
     */
    private ExecutorService asyncWriteExecutor;

    @PostConstruct
    public void init() {
        if (asyncWriteEnabled) {
            asyncWriteExecutor = new ThreadPoolExecutor(
                    asyncWriteThreads,
                    asyncWriteThreads,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(asyncWriteQueueSize),
                    r -> new Thread(r, "redis-write-" + r.hashCode()),
                    new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，在调用线程中执行
            );
            log.info("[RealtimeCacheWriter] 异步写入已启用: threads={}, queueSize={}", 
                    asyncWriteThreads, asyncWriteQueueSize);
        } else {
            log.info("[RealtimeCacheWriter] 异步写入未启用，使用同步写入");
        }
    }

    @PreDestroy
    public void destroy() {
        if (asyncWriteExecutor != null) {
            asyncWriteExecutor.shutdown();
            try {
                if (!asyncWriteExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncWriteExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncWriteExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[RealtimeCacheWriter] 异步写入线程池已关闭");
        }
    }

    /**
     * 写入Hash数据（带采样和时间偏移）
     * <p>
     * 统一的写入方法，包括：
     * 1. 采样检查（通过sampler）
     * 2. Redis写入（同步或异步）
     * 3. 更新最后写入时间
     * 4. 错误处理
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param key Redis键
     * @param payload 数据载荷（Hash字段映射）
     * @param ttlMillis TTL（毫秒）
     * @param now 当前时间戳（毫秒）
     * @param sampleIntervalMillis 采样间隔（毫秒）
     * @param sampler 采样器
     * @return 是否成功写入（false表示被采样过滤，未写入）
     */
    public boolean writeHashWithSampling(Long factoryId, Long deviceId, String key,
                                         Map<String, String> payload, long ttlMillis,
                                         long now, long sampleIntervalMillis,
                                         RealtimeCacheSampler sampler) {
        // 采样检查（使用sampler内部的Caffeine缓存）
        if (!sampler.shouldWriteWithOffset(factoryId, deviceId, key, now, sampleIntervalMillis)) {
            // 被采样过滤，未写入
            return false;
        }

        // 根据配置选择同步或异步写入
        if (asyncWriteEnabled && asyncWriteExecutor != null) {
            // 异步写入，不阻塞主流程
            asyncWriteExecutor.submit(() -> {
                try {
                    redisTemplate.opsForHash().putAll(key, payload);
                    redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
                    sampler.updateLastWriteTime(key, now);
                } catch (Exception e) {
                    log.warn("[RealtimeCacheWriter] 异步写入Redis失败，直接丢弃: key={}, factoryId={}, deviceId={}, error={}",
                            key, factoryId, deviceId, e.getMessage());
                    // 失败直接丢弃，不重试（符合实时数据的低可靠性特性）
                }
            });
            return true;
        } else {
            // 同步写入
            try {
                redisTemplate.opsForHash().putAll(key, payload);
                redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
                sampler.updateLastWriteTime(key, now);
                return true;
            } catch (Exception e) {
                log.error("[RealtimeCacheWriter] 写入Redis失败: key={}, factoryId={}, deviceId={}, error={}",
                        key, factoryId, deviceId, e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * 写入Hash数据（不带采样，直接写入）
     * <p>
     * 适用于不需要采样的场景
     * </p>
     *
     * @param key Redis键
     * @param payload 数据载荷（Hash字段映射）
     * @param ttlMillis TTL（毫秒）
     */
    public void writeHash(String key, Map<String, String> payload, long ttlMillis) {
        // 根据配置选择同步或异步写入
        if (asyncWriteEnabled && asyncWriteExecutor != null) {
            // 异步写入
            asyncWriteExecutor.submit(() -> {
                try {
                    redisTemplate.opsForHash().putAll(key, payload);
                    redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
                } catch (Exception e) {
                    log.warn("[RealtimeCacheWriter] 异步写入Redis失败，直接丢弃: key={}, error={}", 
                            key, e.getMessage());
                    // 失败直接丢弃，不重试
                }
            });
        } else {
            // 同步写入
            try {
                redisTemplate.opsForHash().putAll(key, payload);
                redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
            } catch (Exception e) {
                log.error("[RealtimeCacheWriter] 写入Redis失败: key={}, error={}", key, e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * 写入List数据（追加）
     * <p>
     * 适用于曲线数据等List结构
     * </p>
     *
     * @param key Redis键
     * @param value 数据值
     * @param maxLen 最大长度（0表示不限制）
     * @param ttlMillis TTL（毫秒）
     */
    public void writeList(String key, String value, int maxLen, long ttlMillis) {
        // 根据配置选择同步或异步写入
        if (asyncWriteEnabled && asyncWriteExecutor != null) {
            // 异步写入
            asyncWriteExecutor.submit(() -> {
                try {
                    redisTemplate.opsForList().leftPush(key, value);
                    if (maxLen > 0) {
                        redisTemplate.opsForList().trim(key, 0, maxLen - 1);
                    }
                    redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
                } catch (Exception e) {
                    log.warn("[RealtimeCacheWriter] 异步写入Redis List失败，直接丢弃: key={}, error={}", 
                            key, e.getMessage());
                    // 失败直接丢弃，不重试
                }
            });
        } else {
            // 同步写入
            try {
                redisTemplate.opsForList().leftPush(key, value);
                if (maxLen > 0) {
                    redisTemplate.opsForList().trim(key, 0, maxLen - 1);
                }
                redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
            } catch (Exception e) {
                log.error("[RealtimeCacheWriter] 写入Redis List失败: key={}, error={}", key, e.getMessage(), e);
                throw e;
            }
        }
    }
}
