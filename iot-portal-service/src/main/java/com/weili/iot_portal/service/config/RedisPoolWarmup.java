package com.weili.iot_portal.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redis连接池预热组件
 * <p>
 * 应用启动时预热Redis连接池，避免冷启动时获取连接的延迟
 * </p>
 * <p>
 * 优化说明：
 * 1. 应用启动时预热连接池到min-idle
 * 2. 减少冷启动时获取连接的延迟
 * 3. 提高高并发场景下的连接池稳定性
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class RedisPoolWarmup {

    @Autowired(required = false)
    private LettuceConnectionFactory connectionFactory;

    /**
     * 预热连接数
     * Apollo配置：spring.data.redis.lettuce.pool.warmup-count
     * 默认值：10（与min-idle保持一致）
     */
    @Value("${spring.data.redis.lettuce.pool.warmup-count:10}")
    private int warmupCount;

    /**
     * 预热超时时间（秒）
     * Apollo配置：spring.data.redis.lettuce.pool.warmup-timeout-seconds
     * 默认值：10秒
     */
    @Value("${spring.data.redis.lettuce.pool.warmup-timeout-seconds:10}")
    private long warmupTimeoutSeconds;

    @PostConstruct
    public void warmup() {
        if (connectionFactory == null) {
            log.warn("[RedisPoolWarmup] LettuceConnectionFactory未找到，跳过预热");
            return;
        }

        // 验证预热连接数
        if (warmupCount <= 0) {
            log.warn("[RedisPoolWarmup] 预热连接数配置无效: {}，使用默认值: 10", warmupCount);
            warmupCount = 10;
        }

        log.info("[RedisPoolWarmup] 开始预热Redis连接池: warmupCount={}, timeout={}s", warmupCount, warmupTimeoutSeconds);

        ExecutorService executor = Executors.newFixedThreadPool(warmupCount);
        CountDownLatch latch = new CountDownLatch(warmupCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // 并发预热连接
        for (int i = 0; i < warmupCount; i++) {
            final int index = i;
            executor.submit(() -> {
                RedisConnection connection = null;
                try {
                    connection = connectionFactory.getConnection();
                    if (connection != null) {
                        // 执行PING命令，验证连接可用
                        connection.ping();
                        log.debug("[RedisPoolWarmup] 预热连接成功: index={}, thread={}", 
                                index, Thread.currentThread().getName());
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("[RedisPoolWarmup] 预热连接失败: index={}, error={}", index, e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (Exception e) {
                            log.debug("[RedisPoolWarmup] 关闭预热连接失败: index={}", index, e);
                        }
                    }
                    latch.countDown();
                }
            });
        }

        try {
            // 等待所有预热任务完成
            boolean completed = latch.await(warmupTimeoutSeconds, TimeUnit.SECONDS);
            long cost = System.currentTimeMillis() - startTime;

            int success = successCount.get();
            int fail = failCount.get();
            
            if (completed) {
                log.info("[RedisPoolWarmup] Redis连接池预热完成: success={}, fail={}, cost={}ms",
                        success, fail, cost);
            } else {
                log.warn("[RedisPoolWarmup] Redis连接池预热超时: success={}, fail={}, cost={}ms",
                        success, fail, cost);
            }

            // 如果失败率过高，发出警告
            if (fail > warmupCount * 0.5) {
                log.error("[RedisPoolWarmup] ⚠️ Redis连接池预热失败率过高: success={}, fail={}, failRate={}%",
                        success, fail, (fail * 100.0 / warmupCount));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[RedisPoolWarmup] 预热被中断");
        } finally {
            executor.shutdown();
        }
    }
}
