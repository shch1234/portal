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
 * 设备程序缓存服务
 * <p>
 * 统一管理设备程序相关的缓存操作
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProgramCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.program.ttl-millis:300000}")
    private long programTtlMillis;

    /**
     * 默认空值占位符
     */
    private static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 程序数据缓存 ====================
    /**
     * 保存或更新设备程序缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param programData 程序数据映射（key 为字段名，value 为字段值）
     * @param updatedAt 更新时间戳（毫秒）
     * @param source 数据来源
     * @param traceId 追踪ID（可选）
     */
    public void saveProgram(String factoryId, String deviceId, Map<String, String> programData,
                            long updatedAt, String source, String traceId) {
        if (programData == null || programData.isEmpty()) {
            return;
        }
        Map<String, String> payload = new HashMap<>(programData);
        payload.put("updatedAt", String.valueOf(updatedAt));
        payload.put("source", source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put("traceId", traceId);
        }
        String key = buildProgramKey(factoryId, deviceId);
        redisTemplate.opsForHash().putAll(key, payload);
        redisTemplate.expire(key, Duration.ofMillis(programTtlMillis));
    }

    /**
     * 获取设备程序缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 程序数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getProgram(String factoryId, String deviceId) {
        String key = buildProgramKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 获取程序名称
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return 程序名称，如果不存在返回 null
     */
    public String getProgramName(String factoryId, String deviceId) {
        String key = buildProgramKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, "programName");
        return value != null ? value.toString() : null;
    }

    /**
     * 删除设备程序缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     */
    public void deleteProgram(String factoryId, String deviceId) {
        String key = buildProgramKey(factoryId, deviceId);
        redisTemplate.delete(key);
    }

    // ==================== 辅助方法 ====================
    /**
     * 构建程序缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildProgramKey(String factoryId, String deviceId) {
        return String.format(RedisConstant.RT_PROGRAM,
                defaultBlank(factoryId), defaultBlank(deviceId));
    }

    /**
     * 默认空值处理
     */
    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, DEFAULT_BLANK_PLACEHOLDER);
    }
}

