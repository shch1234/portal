package com.weili.iot_portal.service.cache;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 设备-工厂关系缓存
 */
@Service
public class DeviceFactoryCacheService {

    private static final long DEFAULT_TTL_SECONDS = Duration.ofHours(6).toSeconds();

    @Resource
    private RedisClient redisClient;

    public String get(String deviceId) {
        return redisClient.get(formatKey(deviceId));
    }

    public String getOrLoad(String deviceId, Supplier<String> loader) {
        String cached = get(deviceId);
        if (StringUtils.isNotBlank(cached)) {
            return cached;
        }
        String factoryId = loader.get();
        if (StringUtils.isBlank(factoryId)) {
            return null;
        }
        cache(deviceId, factoryId);
        return factoryId;
    }

    public void cache(String deviceId, String factoryId) {
        if (StringUtils.isBlank(factoryId)) {
            evict(deviceId);
            return;
        }
        redisClient.set(formatKey(deviceId), factoryId, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public void evict(String deviceId) {
        redisClient.delete(formatKey(deviceId));
    }

    private String formatKey(String deviceId) {
        return String.format(RedisConstant.DEVICE_FACTORY, deviceId);
    }
}

