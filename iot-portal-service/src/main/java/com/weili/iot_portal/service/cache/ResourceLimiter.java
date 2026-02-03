package com.weili.iot_portal.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 资源限流器
 * <p>
 * 用于限制并发访问Redis和MySQL，避免资源耗尽
 * 采用Semaphore实现，支持超时获取
 * </p>
 * <p>
 * 优化说明：
 * 1. Redis Pipeline并发限制：避免连接池耗尽
 * 2. MySQL更新并发限制：按设备ID分片，避免行锁竞争
 * 3. 支持动态配置，通过Apollo调整
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class ResourceLimiter {

    /**
     * Redis Pipeline最大并发数
     * Apollo配置：resource.limiter.redis.pipeline.max-concurrent
     * 默认值：10（支持10个任务同时使用Pipeline）
     */
    @Value("${resource.limiter.redis.pipeline.max-concurrent:10}")
    private int redisPipelineMaxConcurrent;

    /**
     * Redis Pipeline获取超时时间（秒）
     * Apollo配置：resource.limiter.redis.pipeline.acquire-timeout-seconds
     * 默认值：3秒
     */
    @Value("${resource.limiter.redis.pipeline.acquire-timeout-seconds:3}")
    private int redisPipelineAcquireTimeoutSeconds;

    /**
     * MySQL设备更新最大并发数（每个设备）
     * Apollo配置：resource.limiter.mysql.device-update.max-concurrent
     * 默认值：1（每个设备串行处理，避免行锁竞争）
     */
    @Value("${resource.limiter.mysql.device-update.max-concurrent:1}")
    private int mysqlDeviceUpdateMaxConcurrent;

    /**
     * MySQL设备更新获取超时时间（秒）
     * Apollo配置：resource.limiter.mysql.device-update.acquire-timeout-seconds
     * 默认值：5秒
     */
    @Value("${resource.limiter.mysql.device-update.acquire-timeout-seconds:5}")
    private int mysqlDeviceUpdateAcquireTimeoutSeconds;

    /**
     * Redis Pipeline信号量
     */
    private Semaphore redisPipelineSemaphore;

    /**
     * MySQL设备更新信号量（按设备ID分片）
     * Key: deviceId, Value: Semaphore（每个设备独立限流）
     */
    private final ConcurrentHashMap<Long, Semaphore> deviceUpdateSemaphores = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 验证并初始化Redis Pipeline信号量
        if (redisPipelineMaxConcurrent <= 0) {
            log.warn("[ResourceLimiter] redisPipelineMaxConcurrent配置无效: {}，使用默认值: 10", redisPipelineMaxConcurrent);
            redisPipelineMaxConcurrent = 10;
        }
        redisPipelineSemaphore = new Semaphore(redisPipelineMaxConcurrent, true); // 公平锁

        // 验证MySQL配置
        if (mysqlDeviceUpdateMaxConcurrent <= 0) {
            log.warn("[ResourceLimiter] mysqlDeviceUpdateMaxConcurrent配置无效: {}，使用默认值: 1", mysqlDeviceUpdateMaxConcurrent);
            mysqlDeviceUpdateMaxConcurrent = 1;
        }

        log.info("[ResourceLimiter] 初始化完成: redisPipelineMaxConcurrent={}, redisPipelineAcquireTimeout={}s, " +
                        "mysqlDeviceUpdateMaxConcurrent={}, mysqlDeviceUpdateAcquireTimeout={}s",
                redisPipelineMaxConcurrent, redisPipelineAcquireTimeoutSeconds,
                mysqlDeviceUpdateMaxConcurrent, mysqlDeviceUpdateAcquireTimeoutSeconds);
    }

    /**
     * 尝试获取Redis Pipeline许可
     *
     * @return true 如果成功获取许可
     */
    public boolean tryAcquireRedisPipeline() {
        try {
            boolean acquired = redisPipelineSemaphore.tryAcquire(redisPipelineAcquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[ResourceLimiter] 获取Redis Pipeline许可超时: timeout={}s, available={}, waiting={}",
                        redisPipelineAcquireTimeoutSeconds,
                        redisPipelineSemaphore.availablePermits(),
                        getWaitingThreadsCount(redisPipelineSemaphore));
            } else {
                log.debug("[ResourceLimiter] 成功获取Redis Pipeline许可: available={}",
                        redisPipelineSemaphore.availablePermits());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ResourceLimiter] 获取Redis Pipeline许可被中断", e);
            return false;
        }
    }

    /**
     * 释放Redis Pipeline许可
     */
    public void releaseRedisPipeline() {
        redisPipelineSemaphore.release();
        log.debug("[ResourceLimiter] 释放Redis Pipeline许可: available={}",
                redisPipelineSemaphore.availablePermits());
    }

    /**
     * 尝试获取MySQL设备更新许可
     *
     * @param deviceId 设备ID
     * @return true 如果成功获取许可
     */
    public boolean tryAcquireDeviceUpdate(Long deviceId) {
        if (deviceId == null) {
            log.warn("[ResourceLimiter] deviceId为空，无法获取许可");
            return false;
        }

        // 获取或创建设备对应的信号量（每个设备独立限流）
        Semaphore semaphore = deviceUpdateSemaphores.computeIfAbsent(deviceId, k -> {
            log.debug("[ResourceLimiter] 创建设备更新信号量: deviceId={}, maxConcurrent={}", deviceId, mysqlDeviceUpdateMaxConcurrent);
            return new Semaphore(mysqlDeviceUpdateMaxConcurrent, true); // 公平锁
        });

        try {
            boolean acquired = semaphore.tryAcquire(mysqlDeviceUpdateAcquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[ResourceLimiter] 获取设备更新许可超时: deviceId={}, timeout={}s, available={}",
                        deviceId, mysqlDeviceUpdateAcquireTimeoutSeconds, semaphore.availablePermits());
            } else {
                log.debug("[ResourceLimiter] 成功获取设备更新许可: deviceId={}, available={}",
                        deviceId, semaphore.availablePermits());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ResourceLimiter] 获取设备更新许可被中断: deviceId={}", deviceId, e);
            return false;
        }
    }

    /**
     * 释放MySQL设备更新许可
     *
     * @param deviceId 设备ID
     */
    public void releaseDeviceUpdate(Long deviceId) {
        if (deviceId == null) {
            return;
        }

        Semaphore semaphore = deviceUpdateSemaphores.get(deviceId);
        if (semaphore != null) {
            semaphore.release();
            log.debug("[ResourceLimiter] 释放设备更新许可: deviceId={}, available={}",
                    deviceId, semaphore.availablePermits());
        }
    }

    /**
     * 获取等待线程数（通过反射，仅用于监控）
     * 注意：Semaphore没有直接提供等待线程数，这里返回0作为占位
     */
    private int getWaitingThreadsCount(Semaphore semaphore) {
        // Semaphore没有提供等待线程数的API，这里返回0
        // 实际可以通过监控工具或JMX获取
        return 0;
    }

    /**
     * 获取Redis Pipeline当前可用许可数（用于监控）
     */
    public int getRedisPipelineAvailablePermits() {
        return redisPipelineSemaphore != null ? redisPipelineSemaphore.availablePermits() : 0;
    }

    /**
     * 获取设备更新信号量数量（用于监控）
     */
    public int getDeviceUpdateSemaphoreCount() {
        return deviceUpdateSemaphores.size();
    }

    /**
     * 清理不再使用的设备信号量（定期调用，避免内存泄漏）
     * 注意：只清理可用许可数等于最大并发数的信号量（表示没有正在使用的）
     */
    public void cleanupUnusedSemaphores() {
        int cleaned = 0;
        for (var it = deviceUpdateSemaphores.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Semaphore semaphore = entry.getValue();
            // 如果可用许可数等于最大并发数，说明没有正在使用的，可以清理
            if (semaphore.availablePermits() == mysqlDeviceUpdateMaxConcurrent) {
                it.remove();
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.debug("[ResourceLimiter] 清理未使用的设备信号量: count={}", cleaned);
        }
    }
}
