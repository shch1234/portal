package com.weili.iot_portal.service.ingestion.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自适应限流服务
 * <p>
 * 实现行业最佳实践：
 * 1. **错误率监控**：滑动窗口监控错误率
 * 2. **自适应限流**：根据错误率动态调整限流阈值
 * 3. **断路器模式**：错误率过高时快速失败，避免雪崩
 * 4. **指数退避恢复**：系统恢复时逐步提高限流阈值
 * </p>
 * <p>
 * 参考实现：
 * - Netflix Hystrix：断路器模式
 * - Google SRE：自适应限流
 * - Alibaba Sentinel：自适应限流和熔断
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdaptiveRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 是否启用自适应限流
     * 默认值：true（启用自适应限流，根据错误率动态调整）
     */
    @Value("${webhook.rate-limit.adaptive-enabled:true}")
    private boolean adaptiveEnabled;

    /**
     * 错误率阈值（百分比）
     * 当错误率超过此阈值时，触发限流降级
     * 默认值：10（10%）
     * 
     * 行业最佳实践：
     * - Google SRE：建议 5-10%
     * - Netflix Hystrix：默认 50%（但通常设置为 10-20%）
     * - Alibaba Sentinel：默认 10%
     */
    @Value("${webhook.rate-limit.adaptive-error-rate-threshold:10}")
    private double errorRateThreshold;

    /**
     * 限流降级比例
     * 当错误率超过阈值时，限流阈值降低到此比例
     * 默认值：0.3（30%，即限流到正常值的30%）
     * 
     * 行业最佳实践：
     * - Google SRE：建议 20-50%
     * - Netflix Hystrix：建议 30-50%
     */
    @Value("${webhook.rate-limit.adaptive-degradation-ratio:0.3}")
    private double degradationRatio;

    /**
     * 恢复阶段数量
     * 系统恢复时，分几个阶段逐步提高限流阈值
     * 默认值：3（分3个阶段恢复：30% -> 50% -> 75% -> 100%）
     */
    @Value("${webhook.rate-limit.adaptive-recovery-stages:3}")
    private int recoveryStages;

    /**
     * 恢复阶段持续时间（秒）
     * 每个恢复阶段持续的时间
     * 默认值：30（每个阶段持续30秒）
     * 
     * 行业最佳实践：
     * - Google SRE：建议 30-60秒
     * - Netflix Hystrix：默认 60秒
     */
    @Value("${webhook.rate-limit.adaptive-recovery-stage-duration-seconds:30}")
    private int recoveryStageDurationSeconds;

    /**
     * 错误率监控窗口大小（秒）
     * 用于计算错误率的滑动窗口大小
     * 默认值：60（60秒窗口）
     * 
     * 行业最佳实践：
     * - Google SRE：建议 60-300秒
     * - Netflix Hystrix：默认 10秒（但通常设置为 60秒）
     */
    @Value("${webhook.rate-limit.adaptive-window-seconds:60}")
    private int windowSeconds;

    /**
     * 最小限流阈值（相对于基础限流值的比例）
     * 即使错误率很高，也不低于此阈值
     * 默认值：0.1（10%，即最低限流到正常值的10%）
     * 
     * 行业最佳实践：
     * - Google SRE：建议 10-20%
     * - Netflix Hystrix：建议 10%
     */
    @Value("${webhook.rate-limit.adaptive-min-ratio:0.1}")
    private double minRatio;

    /**
     * Redis Key 前缀
     */
    private static final String ERROR_COUNT_KEY_PREFIX = "webhook:error-count:";
    private static final String REQUEST_COUNT_KEY_PREFIX = "webhook:request-count:";
    private static final String CIRCUIT_STATE_KEY = "webhook:circuit-state";
    private static final String RECOVERY_STAGE_KEY = "webhook:recovery-stage";
    private static final String RECOVERY_START_TIME_KEY = "webhook:recovery-start-time";

    /**
     * 本地错误计数（用于快速响应，减少 Redis 调用）
     * 每10秒同步一次到 Redis
     */
    private final AtomicLong localErrorCount = new AtomicLong(0);
    private final AtomicLong localRequestCount = new AtomicLong(0);
    private volatile long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 10_000; // 10秒同步一次

    /**
     * 当前限流状态
     */
    private volatile CircuitState circuitState = CircuitState.CLOSED; // CLOSED: 正常, OPEN: 降级, HALF_OPEN: 恢复中
    private volatile int currentRecoveryStage = 0; // 当前恢复阶段（0-3）
    private volatile long recoveryStartTime = 0; // 恢复开始时间

    /**
     * 断路器状态
     */
    private enum CircuitState {
        CLOSED,    // 正常状态：限流正常值
        OPEN,      // 降级状态：限流降低到 degradationRatio
        HALF_OPEN  // 恢复中：逐步提高限流阈值
    }

    @PostConstruct
    public void init() {
        if (adaptiveEnabled) {
            log.info("[AdaptiveRateLimit] 自适应限流初始化完成: " +
                    "errorRateThreshold={}%, degradationRatio={}, recoveryStages={}, " +
                    "recoveryStageDuration={}s, windowSeconds={}s, minRatio={}",
                    errorRateThreshold, degradationRatio, recoveryStages,
                    recoveryStageDurationSeconds, windowSeconds, minRatio);
        } else {
            log.info("[AdaptiveRateLimit] 自适应限流已禁用");
        }
    }

    /**
     * 记录请求（成功）
     */
    public void recordSuccess() {
        if (!adaptiveEnabled) {
            return;
        }

        localRequestCount.incrementAndGet();
        syncToRedisIfNeeded();
    }

    /**
     * 记录请求（失败）
     */
    public void recordError() {
        if (!adaptiveEnabled) {
            return;
        }

        localErrorCount.incrementAndGet();
        localRequestCount.incrementAndGet();
        syncToRedisIfNeeded();
    }

    /**
     * 同步本地计数到 Redis（每10秒同步一次）
     */
    private void syncToRedisIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSyncTime < SYNC_INTERVAL_MS) {
            return;
        }

        synchronized (this) {
            if (currentTime - lastSyncTime < SYNC_INTERVAL_MS) {
                return;
            }

            try {
                long errorCount = localErrorCount.getAndSet(0);
                long requestCount = localRequestCount.getAndSet(0);

                if (errorCount > 0 || requestCount > 0) {
                    long currentWindow = System.currentTimeMillis() / 1000;
                    String errorKey = ERROR_COUNT_KEY_PREFIX + currentWindow;
                    String requestKey = REQUEST_COUNT_KEY_PREFIX + currentWindow;

                    redisTemplate.opsForValue().increment(errorKey, errorCount);
                    redisTemplate.opsForValue().increment(requestKey, requestCount);
                    redisTemplate.expire(errorKey, windowSeconds + 10, TimeUnit.SECONDS);
                    redisTemplate.expire(requestKey, windowSeconds + 10, TimeUnit.SECONDS);
                }

                lastSyncTime = currentTime;
            } catch (Exception e) {
                log.warn("[AdaptiveRateLimit] 同步错误计数到 Redis 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 计算当前错误率
     */
    private double calculateErrorRate() {
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            long windowStart = currentTime - windowSeconds;

            long totalErrors = 0;
            long totalRequests = 0;

            // 统计窗口内的错误数和请求数
            for (long window = windowStart; window <= currentTime; window++) {
                String errorKey = ERROR_COUNT_KEY_PREFIX + window;
                String requestKey = REQUEST_COUNT_KEY_PREFIX + window;

                String errorCountStr = redisTemplate.opsForValue().get(errorKey);
                String requestCountStr = redisTemplate.opsForValue().get(requestKey);

                if (errorCountStr != null) {
                    totalErrors += Long.parseLong(errorCountStr);
                }
                if (requestCountStr != null) {
                    totalRequests += Long.parseLong(requestCountStr);
                }
            }

            // 加上本地计数
            totalErrors += localErrorCount.get();
            totalRequests += localRequestCount.get();

            if (totalRequests == 0) {
                return 0.0;
            }

            return (double) totalErrors / totalRequests * 100.0;
        } catch (Exception e) {
            log.warn("[AdaptiveRateLimit] 计算错误率失败: {}", e.getMessage());
            return 0.0; // 计算失败时，假设错误率为0，不触发限流
        }
    }

    /**
     * 更新断路器状态
     */
    private void updateCircuitState() {
        double errorRate = calculateErrorRate();

        // 从 Redis 读取恢复状态（如果存在）
        try {
            String recoveryStageStr = redisTemplate.opsForValue().get(RECOVERY_STAGE_KEY);
            String recoveryStartTimeStr = redisTemplate.opsForValue().get(RECOVERY_START_TIME_KEY);

            if (recoveryStageStr != null) {
                currentRecoveryStage = Integer.parseInt(recoveryStageStr);
            }
            if (recoveryStartTimeStr != null) {
                recoveryStartTime = Long.parseLong(recoveryStartTimeStr);
            }
        } catch (Exception e) {
            // 忽略读取失败
        }

        CircuitState newState = circuitState;
        int newRecoveryStage = currentRecoveryStage;

        if (errorRate >= errorRateThreshold) {
            // 错误率超过阈值，进入降级状态（无论当前状态是什么）
            if (circuitState != CircuitState.OPEN) {
                log.warn("[AdaptiveRateLimit] 🚨 错误率过高，进入降级状态: errorRate={}%, threshold={}%, previousState={}",
                        String.format("%.2f", errorRate), errorRateThreshold, circuitState);
                newState = CircuitState.OPEN;
                newRecoveryStage = 0;
                recoveryStartTime = 0;
                
                // 保存状态到 Redis
                try {
                    redisTemplate.opsForValue().set(CIRCUIT_STATE_KEY, "OPEN");
                    redisTemplate.opsForValue().set(RECOVERY_STAGE_KEY, "0");
                    redisTemplate.delete(RECOVERY_START_TIME_KEY);
                } catch (Exception e) {
                    log.warn("[AdaptiveRateLimit] 保存状态到 Redis 失败: {}", e.getMessage());
                }
            }
            // 如果已经是 OPEN 状态，保持 OPEN 状态（不需要更新）
        } else if (circuitState == CircuitState.OPEN || circuitState == CircuitState.HALF_OPEN) {
            // 错误率降低，开始恢复
            if (circuitState == CircuitState.OPEN) {
                // 从 OPEN 状态进入 HALF_OPEN 状态
                log.info("[AdaptiveRateLimit] ✅ 错误率降低，开始恢复: errorRate={}%, threshold={}%",
                        String.format("%.2f", errorRate), errorRateThreshold);
                newState = CircuitState.HALF_OPEN;
                newRecoveryStage = 1;
                recoveryStartTime = System.currentTimeMillis();
                
                // 保存状态到 Redis
                try {
                    redisTemplate.opsForValue().set(CIRCUIT_STATE_KEY, "HALF_OPEN");
                    redisTemplate.opsForValue().set(RECOVERY_STAGE_KEY, "1");
                    redisTemplate.opsForValue().set(RECOVERY_START_TIME_KEY, String.valueOf(recoveryStartTime));
                } catch (Exception e) {
                    log.warn("[AdaptiveRateLimit] 保存状态到 Redis 失败: {}", e.getMessage());
                }
            } else if (circuitState == CircuitState.HALF_OPEN) {
                // 在 HALF_OPEN 状态中，检查是否可以进入下一阶段
                long elapsedSeconds = (System.currentTimeMillis() - recoveryStartTime) / 1000;
                
                if (elapsedSeconds >= recoveryStageDurationSeconds) {
                    // 当前阶段持续时间已到，进入下一阶段
                    if (currentRecoveryStage < recoveryStages) {
                        newRecoveryStage = currentRecoveryStage + 1;
                        recoveryStartTime = System.currentTimeMillis();
                        
                        log.info("[AdaptiveRateLimit] 📈 恢复阶段推进: stage={}/{}, errorRate={}%",
                                newRecoveryStage, recoveryStages, String.format("%.2f", errorRate));
                        
                        // 保存状态到 Redis
                        try {
                            redisTemplate.opsForValue().set(RECOVERY_STAGE_KEY, String.valueOf(newRecoveryStage));
                            redisTemplate.opsForValue().set(RECOVERY_START_TIME_KEY, String.valueOf(recoveryStartTime));
                        } catch (Exception e) {
                            log.warn("[AdaptiveRateLimit] 保存状态到 Redis 失败: {}", e.getMessage());
                        }
                    } else {
                        // 所有恢复阶段完成，恢复正常状态
                        log.info("[AdaptiveRateLimit] ✅ 恢复完成，恢复正常限流: errorRate={}%",
                                String.format("%.2f", errorRate));
                        newState = CircuitState.CLOSED;
                        newRecoveryStage = 0;
                        recoveryStartTime = 0;
                        
                        // 保存状态到 Redis
                        try {
                            redisTemplate.opsForValue().set(CIRCUIT_STATE_KEY, "CLOSED");
                            redisTemplate.delete(RECOVERY_STAGE_KEY);
                            redisTemplate.delete(RECOVERY_START_TIME_KEY);
                        } catch (Exception e) {
                            log.warn("[AdaptiveRateLimit] 保存状态到 Redis 失败: {}", e.getMessage());
                        }
                    }
                }
            }
        } else if (circuitState == CircuitState.CLOSED && errorRate < errorRateThreshold * 0.5) {
            // 正常状态，错误率很低，保持正常限流
            // 不需要更新状态
        }

        circuitState = newState;
        currentRecoveryStage = newRecoveryStage;
    }

    /**
     * 获取自适应限流比例
     * <p>
     * 根据当前错误率和断路器状态，返回限流比例（0.0-1.0）
     * </p>
     *
     * @return 限流比例（0.0-1.0），1.0 表示不限流，0.1 表示限流到10%
     */
    public double getAdaptiveRatio() {
        if (!adaptiveEnabled) {
            return 1.0; // 未启用自适应限流，返回100%
        }

        // 更新断路器状态
        updateCircuitState();

        switch (circuitState) {
            case CLOSED:
                // 正常状态：100%
                return 1.0;

            case OPEN:
                // 降级状态：使用 degradationRatio，但不低于 minRatio
                return Math.max(degradationRatio, minRatio);

            case HALF_OPEN:
                // 恢复中：根据恢复阶段逐步提高
                // 阶段1: degradationRatio -> 阶段2: (degradationRatio + 0.5) / 2 -> 阶段3: (degradationRatio + 1.0) / 2 -> 阶段4: 1.0
                double baseRatio = Math.max(degradationRatio, minRatio);
                double stepRatio = (1.0 - baseRatio) / recoveryStages;
                double recoveryRatio = baseRatio + stepRatio * currentRecoveryStage;
                return Math.min(recoveryRatio, 1.0); // 不超过100%

            default:
                return 1.0;
        }
    }

    /**
     * 获取当前状态信息（用于监控和调试）
     */
    public String getStatusInfo() {
        if (!adaptiveEnabled) {
            return "adaptiveRateLimit=disabled";
        }

        double errorRate = calculateErrorRate();
        double adaptiveRatio = getAdaptiveRatio();

        return String.format("adaptiveRateLimit=enabled, circuitState=%s, errorRate=%.2f%%, " +
                "adaptiveRatio=%.2f%%, recoveryStage=%d/%d",
                circuitState, errorRate, adaptiveRatio * 100, currentRecoveryStage, recoveryStages);
    }
}
