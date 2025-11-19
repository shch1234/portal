package com.weili.iot_portal.service.ingestion.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Webhook 幂等服务
 */
@Component
public class WebhookIdempotentService {

    private static final long DEFAULT_TTL_MS = Duration.ofMinutes(10).toMillis();

    private final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();

    /**
     * 尝试消费消息
     *
     * @return true：允许处理；false：已处理
     */
    public boolean tryConsume(String messageId) {
        if (StringUtils.isBlank(messageId)) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = processedMessages.putIfAbsent(messageId, now);
        if (previous != null) {
            return false;
        }
        cleanup(now);
        return true;
    }

    private void cleanup(long now) {
        processedMessages.entrySet().removeIf(entry -> now - entry.getValue() > DEFAULT_TTL_MS);
    }
}

