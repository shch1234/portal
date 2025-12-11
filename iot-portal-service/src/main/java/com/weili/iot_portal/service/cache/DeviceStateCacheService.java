package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
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
 * 设备状态缓存服务
 * <p>
 * 统一管理设备状态相关的缓存操作，包括状态数据、心跳等
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.state.ttl-millis:600000}")
    private long stateTtlMillis;

    @Value("${rt.state.heartbeat-ttl-seconds:300}")
    private long stateHeartbeatTtlSeconds;

    /**
     * 默认空值占位符
     */
    private static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 状态数据缓存 ====================
    /**
     * 保存或更新设备状态缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param state 状态值
     * @param updatedAt 更新时间戳（毫秒）
     * @param source 数据来源
     * @param traceId 追踪ID（可选）
     */
    public void saveState(String factoryId, String deviceId, String state,
                          long updatedAt, String source, String traceId) {
        Map<String, String> payload = new HashMap<>();
        payload.put(DeviceStateEventFields.STATE, state);
        payload.put(DeviceStateEventFields.UPDATED_AT, String.valueOf(updatedAt));
        payload.put(DeviceStateEventFields.SOURCE, source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put(DeviceStateEventFields.TRACE_ID, traceId);
        }
        String key = buildStateKey(factoryId, deviceId);
        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofMillis(stateTtlMillis));
    }

    /**
     * 获取设备状态缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 状态数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getState(String factoryId, String deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 获取设备状态值
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 状态值，如果不存在返回 null
     */
    public String getStateValue(String factoryId, String deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, DeviceStateEventFields.STATE);
        return value != null ? value.toString() : null;
    }

    /**
     * 刷新状态缓存 TTL（不修改值）
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void refreshStateTtl(String factoryId, String deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        redisTemplate.expire(key, Duration.ofMillis(stateTtlMillis));
    }

    /**
     * 删除设备状态缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void deleteState(String factoryId, String deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 状态心跳缓存 ====================
    /**
     * 保存或刷新状态心跳
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param traceId 追踪ID（可选，为空时使用默认值）
     */
    public void saveHeartbeat(String factoryId, String deviceId, String traceId) {
        String key = buildHeartbeatKey(factoryId, deviceId);
        String value = StringUtils.defaultIfBlank(traceId, "1");
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(stateHeartbeatTtlSeconds));
    }

    /**
     * 获取状态心跳值
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 心跳值，如果不存在返回 null
     */
    public String getHeartbeat(String factoryId, String deviceId) {
        String key = buildHeartbeatKey(factoryId, deviceId);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除状态心跳
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void deleteHeartbeat(String factoryId, String deviceId) {
        String key = buildHeartbeatKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================
    /**
     * 构建状态缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildStateKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_STATE,
                defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 构建状态心跳键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildHeartbeatKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_STATE_HEARTBEAT,
                defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 默认空值处理
     */
    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, DEFAULT_BLANK_PLACEHOLDER);
    }
}

