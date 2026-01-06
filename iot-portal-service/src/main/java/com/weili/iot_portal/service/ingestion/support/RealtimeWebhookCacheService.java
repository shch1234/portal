package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RealtimeWebhookCacheService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${webhook.realtime.cache-ttl-seconds:1800}")
    private long realtimeTtlSeconds;

    public void cache(String eventType, String deviceCode, WebhookRequest request) {
        Map<String, Object> value = new HashMap<>();
        value.put("messageId", request.getMessageId());
        value.put("deviceCode", deviceCode);
        value.put("eventType", eventType);
        value.put("timestamp", request.getTimestamp());
        value.put("data", request.getEventData() != null ? request.getEventData() : request.getTelemetryData());
        String key = buildKey(deviceCode, eventType);
        String payload = JsonUtils.toJsonString(value);
        redisTemplate.opsForValue()
                .set(key, payload, Duration.ofSeconds(realtimeTtlSeconds));
    }

    private String buildKey(String deviceCode, String eventType) {
        return String.format(RedisConstant.WEBHOOK_REALTIME_DEVICE, deviceCode, eventType);
    }
}

