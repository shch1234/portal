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

    /**
     * 获取 Hash 的所有字段和值
     * 
     * @param key Redis key
     * @return Hash 的所有字段和值，如果 key 不存在返回 null
     */
    public Map<String, String> getHash(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        // 转换为 Map<String, String>
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<Object, Object> entry : hash.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return result;
    }

    /**
     * 获取 key 的剩余过期时间（秒）
     * 
     * @param key Redis key
     * @return 剩余过期时间（秒），-1 表示 key 不存在，-2 表示 key 没有设置过期时间
     */
    public Long getTtlSeconds(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null) {
            return null;
        }
        // RedisTemplate.getExpire() 返回的是秒数，-1 表示没有过期时间，-2 表示 key 不存在
        return ttl;
    }
}

