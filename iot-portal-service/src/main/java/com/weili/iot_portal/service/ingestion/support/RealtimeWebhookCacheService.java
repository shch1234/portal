package com.weili.iot_portal.service.ingestion.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.basic.common.exception.ServiceException;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${webhook.realtime.cache-ttl-seconds:1800}")
    private long realtimeTtlSeconds;

    public void cache(String eventType, String deviceCode, WebhookRequest request) {
        Map<String, Object> value = new HashMap<>();
        value.put("messageId", request.getMessageId());
        value.put("deviceCode", deviceCode);
        value.put("eventType", eventType);
        value.put("timestamp", request.getTimestamp());
        value.put("data", request.getEventData() != null ? request.getEventData() : request.getTelemetryData());
        try {
            String key = buildKey(deviceCode, eventType);
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue()
                    .set(key, payload, Duration.ofSeconds(realtimeTtlSeconds));
        } catch (JsonProcessingException e) {
            throw new ServiceException(500, "实时数据序列化失败");
        }
    }

    private String buildKey(String deviceCode, String eventType) {
        return String.format("realtime:device:%s:%s", deviceCode, eventType);
    }
}

