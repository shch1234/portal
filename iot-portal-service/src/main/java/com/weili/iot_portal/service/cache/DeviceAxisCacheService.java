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
 * 设备轴数据缓存服务
 * <p>
 * 统一管理设备轴数据相关的缓存操作，包括轴坐标数据、曲线数据等
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAxisCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.axis.ttl-millis:300000}")
    private long axisTtlMillis;

    @Value("${rt.axis.curve.ttl-millis:600000}")
    private long axisCurveTtlMillis;

    /**
     * 默认空值占位符
     */
    private static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 轴数据缓存 ====================
    /**
     * 保存或更新设备轴数据缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param axisData 轴数据映射（key 为字段名，value 为字段值）
     * @param updatedAt 更新时间戳（毫秒）
     * @param source 数据来源
     * @param traceId 追踪ID（可选）
     * @param ratio 倍率值（可选）
     */
    public void saveAxisData(Long factoryId, Long deviceId, Map<String, Object> axisData,
                              long updatedAt, String source, String traceId, Object ratio) {
        if (axisData == null || axisData.isEmpty()) {
            return;
        }
        Map<String, String> payload = new HashMap<>();
        axisData.forEach((k, v) -> payload.put(k, String.valueOf(v)));
        payload.put("updatedAt", String.valueOf(updatedAt));
        payload.put("source", source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put("traceId", traceId);
        }
        if (ratio != null) {
            payload.put("ratio", String.valueOf(ratio));
        }
        String key = buildAxisKey(factoryId, deviceId);
        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofMillis(axisTtlMillis));
    }

    /**
     * 获取设备轴数据缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 轴数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getAxisData(Long factoryId, Long deviceId) {
        String key = buildAxisKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 删除设备轴数据缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void deleteAxisData(Long factoryId, Long deviceId) {
        String key = buildAxisKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 轴曲线数据缓存 ====================
    /**
     * 追加轴曲线数据点
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param metric 指标名称（load/rpm/feed）
     * @param timestamp 时间戳（毫秒）
     * @param value 指标值
     * @param maxLen 最大长度
     */
    public void appendCurvePoint(Long factoryId, Long deviceId, String metric,
                                 long timestamp, Object value, int maxLen) {
        if (value == null) {
            return;
        }
        String pointJson = String.format("{\"ts\":%d,\"value\":%s}", timestamp, value);
        String key = buildCurveKey(factoryId, deviceId, metric);
        redisTemplate.opsForList().leftPush(key, pointJson);
        if (maxLen > 0) {
            redisTemplate.opsForList().trim(key, 0, maxLen - 1);
        }
        redisTemplate.expire(key, Duration.ofMillis(axisCurveTtlMillis));
    }

    /**
     * 获取轴曲线数据
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param metric 指标名称
     * @param start 起始索引
     * @param end 结束索引（-1 表示全部）
     * @return 曲线数据点列表
     */
    public java.util.List<String> getCurvePoints(Long factoryId, Long deviceId,
                                                  String metric, long start, long end) {
        String key = buildCurveKey(factoryId, deviceId, metric);
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 删除轴曲线数据
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param metric 指标名称
     */
    public void deleteCurve(Long factoryId, Long deviceId, String metric) {
        String key = buildCurveKey(factoryId, deviceId, metric);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================
    /**
     * 构建轴数据缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildAxisKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_AXIS,
                defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 构建轴曲线数据键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param metric 指标名称
     * @return Redis 键
     */
    private String buildCurveKey(Long factoryId, Long deviceId, String metric) {
        return String.format("rt:axis:curve:%s:%s:%s",
                metric, factoryId, deviceId);
    }

    /**
     * 默认空值处理
     */
    private String defaultBlank(Long value) {
        return value==null? DEFAULT_BLANK_PLACEHOLDER :value.toString();
    }
}



