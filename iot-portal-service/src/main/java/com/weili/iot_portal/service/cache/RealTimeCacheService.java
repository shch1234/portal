package com.weili.iot_portal.service.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 实时数据缓存通用服务
 * 仅提供通用 Redis 操作（Hash 覆盖写、List 追加截断、TTL 读取），不绑定业务语义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Hash 覆盖写 + TTL（毫秒）
     */
    public void hsetWithTtl(String key, Map<String, String> payload, long ttlMillis) {
        if (StringUtils.isBlank(key) || payload == null || payload.isEmpty()) {
            return;
        }
        redisTemplate.opsForHash().putAll(key, payload);
        if (ttlMillis > 0) {
            redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
        }
    }

    /**
     * 追加时间序列点（LPUSH + LTRIM + PEXPIRE）
     */
    public void lpushTrimExpire(String key, String pointJson, int maxLen, long ttlMillis) {
        if (StringUtils.isBlank(pointJson) || StringUtils.isBlank(key)) {
            return;
        }
        redisTemplate.opsForList().leftPush(key, pointJson);
        if (maxLen > 0) {
            redisTemplate.opsForList().trim(key, 0, maxLen - 1);
        }
        if (ttlMillis > 0) {
            redisTemplate.expire(key, Duration.ofMillis(ttlMillis));
        }
    }

    public void setWithTtlSeconds(String key, String value, long ttlSeconds) {
        if (StringUtils.isBlank(key)) {
            return;
        }
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }
}

