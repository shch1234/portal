package com.weili.iot_portal.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 实时缓存采样写入工具类
 * <p>
 * 提供统一的采样写入逻辑，减少代码重复
 * </p>
 * <p>
 * 功能：
 * 1. 计算设备的时间偏移量（基于设备ID的哈希值）
 * 2. 检查是否需要写入（考虑设备时间偏移和采样间隔）
 * 3. 内存管理：使用Caffeine缓存自动清理过期条目，避免内存泄漏
 * </p>
 * <p>
 * 优化说明：
 * - 使用设备ID的哈希值作为偏移量，保证同一设备的偏移量固定
 * - 不同设备的偏移量分散在0到sampleIntervalMillis之间
 * - 让不同设备的写入时间分散，避免集中在同一时间点
 * - 使用Caffeine缓存自动清理过期条目（1小时后自动过期）
 * - 限制最大缓存大小（10000个设备），防止内存溢出
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
public class RealtimeCacheSampler {

    /**
     * 缓存过期时间（小时）
     * Apollo配置：rt.cache.write-time-expire-hours
     * 默认值：1（1小时后自动清理）
     */
    @Value("${rt.cache.write-time-expire-hours:1}")
    private int writeTimeExpireHours;

    /**
     * 缓存最大大小
     * Apollo配置：rt.cache.write-time-max-size
     * 默认值：10000（最多缓存10000个设备）
     */
    @Value("${rt.cache.write-time-max-size:10000}")
    private int writeTimeMaxSize;

    /**
     * 记录每个设备的上次写入时间（用于采样）
     * Key: Redis键（factoryId:deviceId 或 factoryId:deviceId:metric）
     * Value: 上次写入时间戳（毫秒）
     * <p>
     * 使用Caffeine缓存，自动清理不活跃的设备，避免内存泄漏
     * </p>
     * <p>
     * 注意：由于@Value注解在字段初始化后执行，缓存需要在@PostConstruct中初始化
     * </p>
     */
    private Cache<String, Long> lastWriteTimeCache;

    @PostConstruct
    public void init() {
        lastWriteTimeCache = Caffeine.newBuilder()
                .expireAfterWrite(writeTimeExpireHours, TimeUnit.HOURS) // 1小时后自动过期
                .maximumSize(writeTimeMaxSize) // 最多缓存10000个设备
                .build();
        log.info("[RealtimeCacheSampler] 初始化完成: expireHours={}, maxSize={}", 
                writeTimeExpireHours, writeTimeMaxSize);
    }

    /**
     * 获取缓存实例（用于兼容现有代码）
     * <p>
     * 注意：返回的是Caffeine的asMap()视图，可以直接使用Map接口
     * </p>
     *
     * @return 缓存Map视图
     */
    public Map<String, Long> getLastWriteTimeMap() {
        return lastWriteTimeCache != null ? lastWriteTimeCache.asMap() : null;
    }

    /**
     * 更新上次写入时间
     *
     * @param key Redis键
     * @param writeTime 写入时间戳（毫秒）
     */
    public void updateLastWriteTime(String key, long writeTime) {
        lastWriteTimeCache.put(key, writeTime);
    }

    /**
     * 计算设备的时间偏移量（基于设备ID的哈希值）
     * <p>
     * 优化：让不同设备的写入时间分散在采样窗口内，减少瞬时压力
     * - 使用设备ID的哈希值作为偏移量，保证同一设备的偏移量固定
     * - 不同设备的偏移量分散在0到sampleIntervalMillis之间
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param sampleIntervalMillis 采样间隔（毫秒）
     * @return 时间偏移量（0 到 sampleIntervalMillis-1 之间）
     */
    public long calculateDeviceOffset(Long factoryId, Long deviceId, long sampleIntervalMillis) {
        // 使用设备ID的哈希值作为偏移量，使用质数31增加哈希分布的均匀性
        long hash = Math.abs((factoryId * 31L + deviceId) % sampleIntervalMillis);
        return hash;
    }

    /**
     * 检查是否需要写入（考虑设备时间偏移）
     * <p>
     * 优化：让不同设备的写入时间分散，避免集中在同一时间点
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param key Redis键（用于获取上次写入时间）
     * @param now 当前时间戳（毫秒）
     * @param sampleIntervalMillis 采样间隔（毫秒）
     * @return 是否需要写入
     */
    public boolean shouldWriteWithOffset(Long factoryId, Long deviceId, String key,
                                        long now, long sampleIntervalMillis) {
        Long lastWriteTime = lastWriteTimeCache.getIfPresent(key);

        if (lastWriteTime == null) {
            // 首次写入，计算设备的时间偏移量，判断是否应该写入
            long deviceOffset = calculateDeviceOffset(factoryId, deviceId, sampleIntervalMillis);
            long currentWindowStart = (now / sampleIntervalMillis) * sampleIntervalMillis;
            long deviceWriteTime = currentWindowStart + deviceOffset;

            // 如果当前时间已经过了设备应该写入的时间，则写入
            if (now >= deviceWriteTime) {
                return true;
            }
            // 否则等待到设备应该写入的时间
            return false;
        }

        // 非首次写入，检查距离上次写入是否超过采样间隔
        if ((now - lastWriteTime) < sampleIntervalMillis) {
            return false;
        }

        // 计算设备的时间偏移量，确保写入时间分散
        long deviceOffset = calculateDeviceOffset(factoryId, deviceId, sampleIntervalMillis);
        long currentWindowStart = (now / sampleIntervalMillis) * sampleIntervalMillis;
        long deviceWriteTime = currentWindowStart + deviceOffset;

        // 如果当前时间已经过了设备应该写入的时间，则写入
        return now >= deviceWriteTime;
    }

    /**
     * 检查是否需要写入（兼容旧接口，使用外部Map）
     * <p>
     * 注意：此方法用于兼容旧代码，新代码应使用 shouldWriteWithOffset(factoryId, deviceId, key, now, sampleIntervalMillis)
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param key Redis键（用于获取上次写入时间）
     * @param now 当前时间戳（毫秒）
     * @param sampleIntervalMillis 采样间隔（毫秒）
     * @param lastWriteTimeMap 上次写入时间映射（Key: Redis键, Value: 上次写入时间戳）
     * @return 是否需要写入
     * @deprecated 使用 shouldWriteWithOffset(factoryId, deviceId, key, now, sampleIntervalMillis) 替代
     */
    @Deprecated
    public boolean shouldWriteWithOffset(Long factoryId, Long deviceId, String key,
                                        long now, long sampleIntervalMillis,
                                        Map<String, Long> lastWriteTimeMap) {
        Long lastWriteTime = lastWriteTimeMap.get(key);

        if (lastWriteTime == null) {
            // 首次写入，计算设备的时间偏移量，判断是否应该写入
            long deviceOffset = calculateDeviceOffset(factoryId, deviceId, sampleIntervalMillis);
            long currentWindowStart = (now / sampleIntervalMillis) * sampleIntervalMillis;
            long deviceWriteTime = currentWindowStart + deviceOffset;

            // 如果当前时间已经过了设备应该写入的时间，则写入
            if (now >= deviceWriteTime) {
                return true;
            }
            // 否则等待到设备应该写入的时间
            return false;
        }

        // 非首次写入，检查距离上次写入是否超过采样间隔
        if ((now - lastWriteTime) < sampleIntervalMillis) {
            return false;
        }

        // 计算设备的时间偏移量，确保写入时间分散
        long deviceOffset = calculateDeviceOffset(factoryId, deviceId, sampleIntervalMillis);
        long currentWindowStart = (now / sampleIntervalMillis) * sampleIntervalMillis;
        long deviceWriteTime = currentWindowStart + deviceOffset;

        // 如果当前时间已经过了设备应该写入的时间，则写入
        return now >= deviceWriteTime;
    }
}
