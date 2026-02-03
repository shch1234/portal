package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 设备程序实时缓存服务
 * <p>
 * 设备运行程序相关的缓存操作
 * </p>
 * <p>
 * 优化：采样写入（每10秒写入一次，其余数据丢弃）
 * - 减少Redis写入压力
 * - 适用于高频实时数据
 * - 使用Caffeine缓存自动清理过期条目，避免内存泄漏
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProgramCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RealtimeCacheSampler sampler;
    private final RealtimeCacheWriter writer;

    @Value("${rt.program.ttl-millis:300000}")
    private long programTtlMillis;

    /**
     * 采样写入间隔（毫秒）
     * Apollo配置：rt.program.sample-interval-millis
     * 默认值：10000（10秒）
     */
    @Value("${rt.program.sample-interval-millis:10000}")
    private long sampleIntervalMillis;

    // ==================== 程序数据缓存 ====================

    /**
     * 保存或更新设备程序缓存
     * <p>
     * 优化：采样写入（每10秒写入一次，其余数据丢弃）
     * - 减少Redis写入压力
     * - 适用于高频实时数据
     * </p>
     *
     * @param factoryId   工厂ID
     * @param deviceId    设备ID
     * @param programData 程序数据映射（key 为字段名，value 为字段值）
     * @param updatedAt   更新时间戳（毫秒）
     * @param source      数据来源
     * @param traceId     追踪ID（可选）
     */
    public void saveProgram(Long factoryId, Long deviceId, Map<String, String> programData,
                            long updatedAt, String source, String traceId) {
        if (programData == null || programData.isEmpty()) {
            log.warn("[DeviceProgramCacheService] 程序数据为空，跳过写入: factoryId={}, deviceId={}", factoryId, deviceId);
            return;
        }
        
        String key = buildProgramKey(factoryId, deviceId);
        long now = System.currentTimeMillis();
        
        // 构建payload
        Map<String, String> payload = new HashMap<>(programData);
        payload.put("updatedAt", String.valueOf(updatedAt));
        payload.put("source", source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put("traceId", traceId);
        }
        
        // 使用工具类进行采样写入（使用sampler内部的Caffeine缓存）
        boolean written = writer.writeHashWithSampling(
                factoryId, deviceId, key, payload, programTtlMillis,
                now, sampleIntervalMillis, sampler);
        
        if (!written) {
            // 被采样过滤，未写入（正常情况，不记录日志）
            return;
        }
    }

    /**
     * 获取设备程序缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 程序数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getProgram(Long factoryId, Long deviceId) {
        String key = buildProgramKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 获取程序名称
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 程序名称，如果不存在返回 null
     */
    public String getProgramName(Long factoryId, Long deviceId) {
        String key = buildProgramKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, "programName");
        return value != null ? value.toString() : null;
    }


    // ==================== 辅助方法 ====================

    /**
     * 构建程序缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildProgramKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_PROGRAM, factoryId, deviceId);
    }

}




