package com.weili.iot_portal.service.cache;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 设备理论节拍缓存服务
 * <p>
 * 缓存设备理论节拍值，减少数据库查询
 * 理论节拍值相对稳定，适合缓存
 * </p>
 * <p>
 * Redis Key格式：iot_portal:device:theoretical_cycle:{deviceId}
 * Value：理论节拍值（秒，Long类型）
 * TTL：1小时（理论节拍值相对稳定，但需要定期更新）
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTheoreticalCycleCacheService {

    private final RedisClient redisClient;

    /**
     * 缓存TTL基础值（秒）：1小时
     * 理论节拍值相对稳定，但需要定期更新（当有新产量记录时）
     */
    private static final long CACHE_TTL_BASE_SECONDS = 3600L; // 1小时基础值
    
    /**
     * TTL随机偏移范围（秒）：0-300秒（5分钟）
     * 防止多台设备的缓存同时失效，造成缓存雪崩
     * 实际TTL = CACHE_TTL_BASE_SECONDS + random(0, 300)
     */
    private static final long CACHE_TTL_RANDOM_OFFSET_SECONDS = 300L; // 5分钟随机偏移

    /**
     * 获取缓存的理论节拍值
     *
     * @param deviceId 设备ID
     * @return 理论节拍值（秒），如果缓存不存在则返回null
     */
    public Long getCachedTheoreticalCycle(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        
        try {
            String key = buildCacheKey(deviceId);
            String value = redisClient.get(key);
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    log.warn("[DeviceTheoreticalCycleCache] 缓存值格式错误: deviceId={}, value={}", deviceId, value);
                    // 删除无效缓存
                    redisClient.delete(key);
                    return null;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[DeviceTheoreticalCycleCache] 获取缓存失败: deviceId={}, error={}", deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 缓存理论节拍值
     * <p>
     * 缓存策略：
     * 1. 设置TTL为1小时+随机偏移（0-5分钟），防止多台设备缓存同时失效
     * 2. 缓存失效后，下次查询会自动从数据库重新计算并更新缓存
     * 3. 这样既能减少数据库查询，又能保证数据的相对新鲜度，同时避免缓存雪崩
     * </p>
     *
     * @param deviceId 设备ID
     * @param theoreticalCycleSeconds 理论节拍值（秒）
     */
    public void cacheTheoreticalCycle(Long deviceId, long theoreticalCycleSeconds) {
        if (deviceId == null || theoreticalCycleSeconds <= 0) {
            return;
        }
        
        try {
            String key = buildCacheKey(deviceId);
            // 计算TTL：基础1小时 + 随机偏移（0-5分钟），防止缓存雪崩
            long ttlSeconds = CACHE_TTL_BASE_SECONDS + 
                    (long)(Math.random() * CACHE_TTL_RANDOM_OFFSET_SECONDS);
            // 设置缓存，TTL为1小时+随机偏移，防止多台设备同时失效
            redisClient.set(key, String.valueOf(theoreticalCycleSeconds), ttlSeconds, TimeUnit.SECONDS);
            log.debug("[DeviceTheoreticalCycleCache] 缓存理论节拍（TTL带随机偏移，防止缓存雪崩）: deviceId={}, theoreticalCycleSeconds={}, ttl={}秒", 
                    deviceId, theoreticalCycleSeconds, ttlSeconds);
        } catch (Exception e) {
            log.warn("[DeviceTheoreticalCycleCache] 缓存理论节拍失败: deviceId={}, theoreticalCycleSeconds={}, error={}", 
                    deviceId, theoreticalCycleSeconds, e.getMessage());
        }
    }

    /**
     * 删除缓存的理论节拍值
     * <p>
     * 当设备有新产量记录时，可以删除缓存以触发重新计算
     * </p>
     *
     * @param deviceId 设备ID
     */
    public void evictTheoreticalCycle(Long deviceId) {
        if (deviceId == null) {
            return;
        }
        
        try {
            String key = buildCacheKey(deviceId);
            redisClient.delete(key);
            log.debug("[DeviceTheoreticalCycleCache] 删除缓存: deviceId={}", deviceId);
        } catch (Exception e) {
            log.warn("[DeviceTheoreticalCycleCache] 删除缓存失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
    }

    /**
     * 构建缓存键
     *
     * @param deviceId 设备ID
     * @return 缓存键
     */
    private String buildCacheKey(Long deviceId) {
        return String.format(RedisConstant.DEVICE_THEORETICAL_CYCLE, deviceId);
    }
}
