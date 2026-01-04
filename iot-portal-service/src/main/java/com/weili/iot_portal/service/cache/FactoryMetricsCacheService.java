package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.domain.ingestion.FactoryRealtimeMetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工厂实时指标缓存服务
 * <p>
 * 工厂级OEE相关指标的实时缓存（聚合设备级指标）
 * </p>
 * Redis Hash结构：
 * {
 *   "metric.oee": "84.5",
 *   "metric.uptimeRate": "95.5",
 *   "metric.performanceRate": "88.2",
 *   "metric.availabilityRate": "92.3",
 *   "metric.faultRate": "2.1",
 *   "meta.sumWeight": "28800",
 *   "meta.validDevices": "45",
 *   "meta.totalDevices": "50",
 *   "meta.dataCompleteness": "0.9",
 *   "updatedAt": "1731470400"
 * }
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FactoryMetricsCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${factory.metrics.ttl-seconds:600}")
    private long ttlSeconds;

    // ==================== 指标数据缓存 ====================

    /**
     * 保存工厂实时指标快照到缓存
     *
     * @param factoryId 工厂ID
     * @param snapshot  工厂指标快照
     */
    public void saveFactoryRealtimeMetrics(Long factoryId, FactoryRealtimeMetricSnapshot snapshot) {
        String key = buildFactoryMetricKey(factoryId);
        Map<String, String> payload = new HashMap<>();
        payload.put("metric.oee", snapshot.getOee().toPlainString());
        payload.put("metric.uptimeRate", snapshot.getUptimeRate().toPlainString());
        payload.put("metric.performanceRate", snapshot.getPerformanceRate().toPlainString());
        payload.put("metric.availabilityRate", snapshot.getAvailabilityRate().toPlainString());
        payload.put("metric.faultRate", snapshot.getFaultRate().toPlainString());
        payload.put("meta.sumWeight", String.valueOf(snapshot.getSumWeight()));
        payload.put("meta.validDevices", String.valueOf(snapshot.getValidDevices()));
        payload.put("meta.totalDevices", String.valueOf(snapshot.getTotalDevices()));
        payload.put("meta.dataCompleteness", snapshot.getDataCompleteness().toPlainString());
        payload.put("updatedAt", String.valueOf(snapshot.getUpdatedAtSec()));

        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 获取工厂实时指标快照
     *
     * @param factoryId 工厂ID
     * @return 工厂指标快照（如不存在则为空）
     */
    public Optional<FactoryRealtimeMetricSnapshot> getFactoryRealtimeMetrics(Long factoryId) {
        String key = buildFactoryMetricKey(factoryId);
        Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal oee = parseBigDecimal(map.get("metric.oee"));
            BigDecimal uptimeRate = parseBigDecimal(map.get("metric.uptimeRate"));
            BigDecimal performanceRate = parseBigDecimal(map.get("metric.performanceRate"));
            BigDecimal availabilityRate = parseBigDecimal(map.get("metric.availabilityRate"));
            BigDecimal faultRate = parseBigDecimal(map.get("metric.faultRate"));
            long sumWeight = parseLong(map.get("meta.sumWeight"), 0L);
            int validDevices = parseInt(map.get("meta.validDevices"), 0);
            int totalDevices = parseInt(map.get("meta.totalDevices"), 0);
            BigDecimal dataCompleteness = parseBigDecimal(map.get("meta.dataCompleteness"));
            long updatedAt = parseLong(map.get("updatedAt"), 0L);
            
            return Optional.of(new FactoryRealtimeMetricSnapshot(
                    oee, uptimeRate, performanceRate, availabilityRate, faultRate,
                    sumWeight, validDevices, totalDevices, dataCompleteness, updatedAt));
        } catch (Exception e) {
            log.warn("[FactoryMetricsCache] 解析工厂实时指标失败: key={}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 过期/删除缓存
     *
     * @param factoryId 工厂ID
     */
    public void evictFactoryRealtimeMetrics(Long factoryId) {
        String key = buildFactoryMetricKey(factoryId);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建工厂指标缓存键
     *
     * @param factoryId 工厂ID
     * @return Redis 键
     */
    private String buildFactoryMetricKey(Long factoryId) {
        return String.format(RedisConstant.RT_FACTORY_METRIC, defaultBlank(factoryId));
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
            log.warn("[FactoryMetricsCache] 解析BigDecimal失败: value={}", value, e);
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
            log.warn("[FactoryMetricsCache] 解析Long失败: value={}", value, e);
            return defaultVal;
        }
    }

    /**
     * 解析Integer
     *
     * @param value      值
     * @param defaultVal 默认值
     * @return Integer，如果解析失败返回默认值
     */
    private int parseInt(Object value, int defaultVal) {
        if (value == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("[FactoryMetricsCache] 解析Integer失败: value={}", value, e);
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

