package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
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
 * 设备刀具缓存服务
 * <p>
 * 统一管理设备刀具相关的缓存操作
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceToolCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.tool.ttl-millis:300000}")
    private long toolTtlMillis;

    /**
     * 默认空值占位符
     */
    private static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 刀具数据缓存 ====================
    /**
     * 保存或更新设备刀具缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param toolData 刀具数据映射（key 为字段名，value 为字段值）
     * @param updatedAt 更新时间戳（毫秒）
     * @param source 数据来源
     * @param traceId 追踪ID（可选）
     */
    public void saveTool(String factoryId, String deviceId, Map<String, String> toolData,
                         long updatedAt, String source, String traceId) {
        if (toolData == null || toolData.isEmpty()) {
            return;
        }
        Map<String, String> payload = new HashMap<>(toolData);
        payload.put(DeviceToolEventFields.UPDATED_AT, String.valueOf(updatedAt));
        payload.put(DeviceToolEventFields.SOURCE, source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put(DeviceToolEventFields.TRACE_ID, traceId);
        }
        String key = buildToolKey(factoryId, deviceId);
        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofMillis(toolTtlMillis));
    }

    /**
     * 获取设备刀具缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 刀具数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getTool(String factoryId, String deviceId) {
        String key = buildToolKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 获取刀具编号
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 刀具编号，如果不存在返回 null
     */
    public String getToolNumber(String factoryId, String deviceId) {
        String key = buildToolKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, DeviceToolEventFields.TOOL_NUMBER);
        return value != null ? value.toString() : null;
    }

    /**
     * 删除设备刀具缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void deleteTool(String factoryId, String deviceId) {
        String key = buildToolKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================
    /**
     * 构建刀具缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildToolKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_TOOL,
                defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 默认空值处理
     */
    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, DEFAULT_BLANK_PLACEHOLDER);
    }
}



