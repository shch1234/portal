package com.weili.iot_portal.service.ingestion.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Webhook 幂等服务
 */
@Component
public class WebhookIdempotentService {

    private static final String KEY_PREFIX = "webhook:idempotent:";
    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(24).getSeconds();

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 尝试消费消息
     *
     * @return true：允许处理；false：已处理
     */
    public boolean tryConsume(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return true;
        }
        String key = KEY_PREFIX + messageId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(DEFAULT_TTL_SECONDS));
        return Boolean.TRUE.equals(success);
    }
}

