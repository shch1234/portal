package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * Webhook 幂等服务
 * <p>
 * 优化说明：
 * 1. TTL 改为可配置，支持通过 Apollo 动态调整
 * 2. 默认 TTL 从 24 小时缩短为 30 分钟，减少 Redis 内存占用
 * 3. 添加配置验证，防止配置错误（最小 5 分钟，最大 7 天）
 * 4. 30 分钟符合 Webhook 实时事件场景的最佳实践
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
     * 验证后的 TTL（秒）
     */
    private long ttlSeconds;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

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
            log.info("[Webhook-Idempotent] 幂等性缓存配置: TTL={}秒（{}分钟）", 
                    ttlSeconds, ttlSeconds / 60);
        } else {
            log.info("[Webhook-Idempotent] 幂等性缓存配置: TTL={}秒（{}小时）", 
                    ttlSeconds, ttlSeconds / 3600);
        }
    }

    /**
     * 尝试消费消息
     *
     * @return true：允许处理；false：已处理
     */
    public boolean tryConsume(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return true;
        }
        String key = RedisConstant.WEBHOOK_IDEMPOTENT + messageId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(success);
    }
}

