package com.weili.iot_portal.service.ingestion.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 限流服务
 * <p>
 * 使用 Redis 实现分布式限流，防止请求量过大导致系统过载
 * </p>
 * <p>
 * 限流策略：滑动窗口算法
 * - 每秒允许的请求数：webhook.rate-limit.permits-per-second（默认 2000）
 * - 限流窗口大小：1 秒
 * </p>
 * <p>
 * 配置说明：
 * - webhook.rate-limit.enabled: 是否启用限流（默认 true）
 * - webhook.rate-limit.permits-per-second: 每秒允许的请求数（默认 2000）
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 是否启用限流
     * 默认值：true
     */
    @Value("${webhook.rate-limit.enabled:true}")
    private boolean enabled;

    /**
     * 每秒允许的请求数（正常运行时的限流值）
     * 默认值：2000（根据系统处理能力调整）
     * 
     * 计算依据：
     * - 线程池：200 线程
     * - 平均处理时间：假设 100ms
     * - 理论吞吐量：200 / 0.1 = 2000 QPS
     * - 考虑队列缓冲：2000 * 1.5 = 3000 QPS（但保守设置为 2000）
     */
    @Value("${webhook.rate-limit.permits-per-second:2000}")
    private int permitsPerSecond;

    /**
     * 是否启用冷启动限流
     * 默认值：true（启用冷启动限流，保护系统启动时不被瞬时流量打爆）
     */
    @Value("${webhook.rate-limit.cold-start-enabled:true}")
    private boolean coldStartEnabled;

    /**
     * 冷启动限流阶段1：启动后0-30秒的限流比例
     * 默认值：0.5（50%，即限流到正常值的50%）
     */
    @Value("${webhook.rate-limit.cold-start-phase1-ratio:0.5}")
    private double coldStartPhase1Ratio;

    /**
     * 冷启动限流阶段2：启动后30-60秒的限流比例
     * 默认值：0.75（75%，即限流到正常值的75%）
     */
    @Value("${webhook.rate-limit.cold-start-phase2-ratio:0.75}")
    private double coldStartPhase2Ratio;

    /**
     * 冷启动阶段1持续时间（秒）
     * 默认值：30（启动后0-30秒使用阶段1限流）
     */
    @Value("${webhook.rate-limit.cold-start-phase1-duration-seconds:30}")
    private int coldStartPhase1DurationSeconds;

    /**
     * 冷启动阶段2持续时间（秒）
     * 默认值：30（启动后30-60秒使用阶段2限流）
     */
    @Value("${webhook.rate-limit.cold-start-phase2-duration-seconds:30}")
    private int coldStartPhase2DurationSeconds;

    /**
     * 应用启动时间（毫秒）
     * 在 ApplicationReadyEvent 触发时记录
     */
    private volatile long applicationStartTime = 0;

    /**
     * 上次限流警告时间（秒级窗口），用于频率限制
     */
    private volatile long lastWarnWindow = 0;

    /**
     * 冷启动阶段1最后输出日志的时间（秒），用于避免重复日志
     */
    private volatile long lastPhase1LogTime = -1;

    /**
     * 冷启动阶段2最后输出日志的时间（秒），用于避免重复日志
     */
    private volatile long lastPhase2LogTime = -1;

    /**
     * 冷启动完成日志是否已输出
     */
    private volatile boolean coldStartCompleteLogged = false;

    /**
     * Redis 限流 Key 前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "webhook:rate-limit:";

    /**
     * 初始化：记录启动时间（如果ApplicationReadyEvent未触发，使用当前时间作为fallback）
     */
    @PostConstruct
    public void init() {
        // 如果ApplicationReadyEvent未触发，使用当前时间作为fallback
        if (applicationStartTime == 0) {
            applicationStartTime = System.currentTimeMillis();
            log.info("[Webhook-RateLimit] 初始化完成: coldStartEnabled={}, permitsPerSecond={}", 
                    coldStartEnabled, permitsPerSecond);
        }
    }

    /**
     * 监听应用就绪事件，记录启动时间
     * <p>
     * 应用完全启动后，开始计算冷启动限流时间
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        applicationStartTime = System.currentTimeMillis();
        log.info("[Webhook-RateLimit] 应用就绪，开始冷启动限流保护: startTime={}, " +
                "coldStartEnabled={}, phase1Ratio={}, phase2Ratio={}, " +
                "phase1Duration={}s, phase2Duration={}s",
                applicationStartTime, coldStartEnabled, 
                coldStartPhase1Ratio, coldStartPhase2Ratio,
                coldStartPhase1DurationSeconds, coldStartPhase2DurationSeconds);
    }

    /**
     * 计算当前有效的限流阈值（考虑冷启动）
     * <p>
     * 冷启动限流策略（行业最佳实践）：
     * - 启动后0-30秒：限流50%（保护系统初始化，避免瞬时流量打爆数据库）
     * - 启动后30-60秒：限流75%（逐渐放开，给系统时间完成缓存预热等）
     * - 启动后60秒以上：限流100%（正常运行）
     * </p>
     * <p>
     * 日志优化：每个阶段每个10秒窗口只输出一次日志，避免并发请求导致日志刷屏
     * </p>
     *
     * @return 当前有效的限流阈值
     */
    private int getCurrentLimit() {
        if (!coldStartEnabled || applicationStartTime == 0) {
            return permitsPerSecond;
        }

        long uptimeSeconds = (System.currentTimeMillis() - applicationStartTime) / 1000;

        if (uptimeSeconds < coldStartPhase1DurationSeconds) {
            // 阶段1：启动后0-30秒，限流50%
            int limit = (int) (permitsPerSecond * coldStartPhase1Ratio);
            // 每个10秒窗口只输出一次日志，避免并发请求导致日志刷屏
            long logWindow = uptimeSeconds / 10;
            if (lastPhase1LogTime != logWindow) {
                lastPhase1LogTime = logWindow;
                log.info("[Webhook-RateLimit] 冷启动阶段1: uptime={}s, limit={} ({}%), normalLimit={}",
                        uptimeSeconds, limit, (int)(coldStartPhase1Ratio * 100), permitsPerSecond);
            }
            return limit;
        } else if (uptimeSeconds < coldStartPhase1DurationSeconds + coldStartPhase2DurationSeconds) {
            // 阶段2：启动后30-60秒，限流75%
            int limit = (int) (permitsPerSecond * coldStartPhase2Ratio);
            // 每个10秒窗口只输出一次日志，避免并发请求导致日志刷屏
            long logWindow = uptimeSeconds / 10;
            if (lastPhase2LogTime != logWindow) {
                lastPhase2LogTime = logWindow;
                log.info("[Webhook-RateLimit] 冷启动阶段2: uptime={}s, limit={} ({}%), normalLimit={}",
                        uptimeSeconds, limit, (int)(coldStartPhase2Ratio * 100), permitsPerSecond);
            }
            return limit;
        } else {
            // 正常运行：启动后60秒以上，限流100%
            if (!coldStartCompleteLogged) {
                coldStartCompleteLogged = true;
                log.info("[Webhook-RateLimit] 冷启动完成，恢复正常限流: uptime={}s, limit={}",
                        uptimeSeconds, permitsPerSecond);
            }
            return permitsPerSecond;
        }
    }

    /**
     * 自适应限流服务（可选，如果未注入则使用固定限流）
     */
    private AdaptiveRateLimiter adaptiveRateLimiter;

    /**
     * 注入自适应限流服务（可选）
     * 通过配置类注入，避免循环依赖
     */
    public void setAdaptiveRateLimiter(AdaptiveRateLimiter adaptiveRateLimiter) {
        this.adaptiveRateLimiter = adaptiveRateLimiter;
    }

    /**
     * 尝试获取限流许可
     * <p>
     * 使用滑动窗口算法实现限流
     * 支持冷启动限流：启动时使用更严格的限流，运行一段时间后逐渐放开
     * 支持自适应限流：根据错误率动态调整限流阈值
     * </p>
     *
     * @return true 如果获取成功，false 如果被限流
     */
    public boolean tryAcquire() {
        if (!enabled) {
            return true;
        }

        try {
            // 获取当前有效的限流阈值（考虑冷启动）
            int baseLimit = getCurrentLimit();

            // 应用自适应限流比例（如果启用）
            int currentLimit = baseLimit;
            if (adaptiveRateLimiter != null) {
                double adaptiveRatio = adaptiveRateLimiter.getAdaptiveRatio();
                currentLimit = (int) (baseLimit * adaptiveRatio);
                // 确保不低于最小限流值（至少允许10%的流量）
                currentLimit = Math.max(currentLimit, (int) (baseLimit * 0.1));
            }

            // 使用 Redis 滑动窗口限流
            // Key: webhook:rate-limit:{timestamp}
            // Value: 当前窗口的请求计数
            long currentWindow = System.currentTimeMillis() / 1000; // 秒级窗口
            String key = RATE_LIMIT_KEY_PREFIX + currentWindow;

            // 使用 Redis INCR 原子操作
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                // 第一次请求，设置过期时间（窗口大小 + 1 秒，防止时间偏差）
                redisTemplate.expire(key, 2, TimeUnit.SECONDS);
            }

            // 检查是否超过限制（使用动态限流阈值）
            if (count > currentLimit) {
                // 频率限制：每10秒最多记录一次警告，避免日志刷屏
                if (currentWindow != lastWarnWindow && currentWindow % 10 == 0) {
                    lastWarnWindow = currentWindow;
                    String adaptiveInfo = adaptiveRateLimiter != null ? 
                            ", " + adaptiveRateLimiter.getStatusInfo() : "";
                    log.warn("[Webhook-RateLimit] 请求被限流: currentCount={}, limit={} (baseLimit={}{}), window={}, uptime={}s",
                            count, currentLimit, baseLimit, adaptiveInfo, currentWindow, 
                            applicationStartTime > 0 ? (System.currentTimeMillis() - applicationStartTime) / 1000 : 0);
                }
                return false;
            }

            return true;
        } catch (Exception e) {
            // Redis 异常时，降级为允许通过（避免 Redis 故障影响业务）
            log.error("[Webhook-RateLimit] 限流检查异常，降级为允许通过", e);
            return true;
        }
    }

    /**
     * 获取当前限流配置信息
     */
    public String getConfigInfo() {
        long uptimeSeconds = applicationStartTime > 0 ? 
                (System.currentTimeMillis() - applicationStartTime) / 1000 : 0;
        int currentLimit = getCurrentLimit();
        return String.format("enabled=%s, permitsPerSecond=%d, currentLimit=%d, " +
                "coldStartEnabled=%s, uptime=%ds", 
                enabled, permitsPerSecond, currentLimit, coldStartEnabled, uptimeSeconds);
    }
}
