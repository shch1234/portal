package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.ingestion.RealtimeMetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备实时指标缓存服务
 * <p>
 * 设备OEE相关指标的实时缓存（时间开动率、性能率、可用率、故障率、OEE等）
 * </p>
 * Redis Hash结构：
 * {
 *   "metric.uptimeRate": "95.5",
 *   "metric.performanceRate": "88.2",
 *   "metric.availabilityRate": "92.3",
 *   "metric.faultRate": "2.1",
 *   "metric.oee": "84.5",
 *   "updatedAt": "1731470400"
 * }
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMetricsCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.metrics.ttl-seconds:600}")
    private long ttlSeconds;

    // ==================== 指标数据缓存 ====================

    /**
     * 保存实时指标快照到缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param snapshot  指标快照
     */
    public void saveRealtimeMetrics(Long factoryId, Long deviceId, RealtimeMetricSnapshot snapshot) {
        String key = buildMetricKey(factoryId, deviceId);
        Map<String, String> payload = new HashMap<>();
        payload.put("metric.uptimeRate", snapshot.getUptimeRate().toPlainString());
        payload.put("metric.performanceRate", snapshot.getPerformanceRate().toPlainString());
        payload.put("metric.availabilityRate", snapshot.getAvailabilityRate().toPlainString());
        payload.put("metric.faultRate", snapshot.getFaultRate().toPlainString());
        payload.put("metric.oee", snapshot.getOee().toPlainString());
        payload.put("updatedAt", String.valueOf(snapshot.getUpdatedAtSec()));

        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 获取单设备的实时指标快照
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 指标快照（如不存在则为空）
     */
    public Optional<RealtimeMetricSnapshot> getDeviceRealtimeMetrics(Long factoryId, Long deviceId) {
        String key = buildMetricKey(factoryId, deviceId);
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal uptime = parseBigDecimal(map.get("metric.uptimeRate"));
            BigDecimal performance = parseBigDecimal(map.get("metric.performanceRate"));
            BigDecimal availability = parseBigDecimal(map.get("metric.availabilityRate"));
            BigDecimal fault = parseBigDecimal(map.get("metric.faultRate"));
            BigDecimal oee = parseBigDecimal(map.get("metric.oee"));
            long updatedAt = parseLong(map.get("updatedAt"), 0L);
            return Optional.of(new RealtimeMetricSnapshot(uptime, performance, availability, fault, oee, updatedAt));
        } catch (Exception e) {
            log.warn("[DeviceMetricsCache] 解析实时指标失败: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 批量获取设备实时指标快照
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表
     * @return 设备ID -> 指标快照 Map
     */
    public Map<Long, RealtimeMetricSnapshot> batchGetDeviceRealtimeMetrics(Long factoryId, List<Long> deviceIds) {
        Map<Long, RealtimeMetricSnapshot> result = new HashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }

        List<String> keys = deviceIds.stream()
                .map(deviceId -> buildMetricKey(factoryId, deviceId))
                .collect(Collectors.toList());

        try {
            // 使用 Pipeline 批量读取，提高性能
            List<Object> pipelineResults = redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (String key : keys) {
                            connection.hGetAll(key.getBytes());
                        }
                        return null;
                    }
            );

            for (int i = 0; i < deviceIds.size() && i < pipelineResults.size(); i++) {
                Long deviceId = deviceIds.get(i);
                Object resultObj = pipelineResults.get(i);
                if (resultObj == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) resultObj;
                if (map != null && !map.isEmpty()) {
                    try {
                        BigDecimal uptime = parseBigDecimal(map.get("metric.uptimeRate"));
                        BigDecimal performance = parseBigDecimal(map.get("metric.performanceRate"));
                        BigDecimal availability = parseBigDecimal(map.get("metric.availabilityRate"));
                        BigDecimal fault = parseBigDecimal(map.get("metric.faultRate"));
                        BigDecimal oee = parseBigDecimal(map.get("metric.oee"));
                        long updatedAt = parseLong(map.get("updatedAt"), 0L);
                        result.put(deviceId, new RealtimeMetricSnapshot(uptime, performance, availability, fault, oee, updatedAt));
                    } catch (Exception e) {
                        log.warn("[DeviceMetricsCache] 解析批量实时指标失败: deviceId={}, key={}", deviceId, keys.get(i), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[DeviceMetricsCache] 批量读取实时指标失败: factoryId={}, deviceCount={}", factoryId, deviceIds.size(), e);
            // 降级为逐个读取
            for (Long deviceId : deviceIds) {
                Optional<RealtimeMetricSnapshot> snapOpt = getDeviceRealtimeMetrics(factoryId, deviceId);
                snapOpt.ifPresent(snap -> result.put(deviceId, snap));
            }
        }

        return result;
    }

    /**
     * 过期/删除缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     */
    public void evictRealtimeMetrics(Long factoryId, Long deviceId) {
        String key = buildMetricKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建指标缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildMetricKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_METRIC, defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 解析BigDecimal
     *
     * @param value 值
     * @return BigDecimal，如果解析失败返回ZERO
     */
    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.warn("[DeviceMetricsCache] 解析BigDecimal失败: value={}", value, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析Long
     *
     * @param value      值
     * @param defaultVal 默认值
     * @return Long，如果解析失败返回默认值
     */
    private long parseLong(Object value, long defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("[DeviceMetricsCache] 解析Long失败: value={}", value, e);
            return defaultVal;
        }
    }

    /**
     * 默认空白值处理
     *
     * @param value 值
     * @return 字符串，如果为null返回"none"
     */
    private String defaultBlank(Long value) {
        return value == null ? "none" : value.toString();
    }
}

