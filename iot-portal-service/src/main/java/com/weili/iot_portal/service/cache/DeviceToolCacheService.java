package com.weili.iot_portal.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weili.basic.common.util.JsonUtils;
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
 * 设备刀具实时缓存服务
 * <p>
 * 当前设备刀具相关的缓存操作
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

    @Value("${compensation.cache.ttl-seconds:86400}")
    private long compensationCacheTtlSeconds;

    // ==================== 刀具数据缓存 ====================

    /**
     * 保存或更新设备刀具缓存
     * <p>
     * 存储格式：包含 toolNo、holderNumber 和 compensation 的完整JSON结构
     * </p>
     *
     * @param factoryId    工厂ID
     * @param deviceId     设备ID
     * @param toolNo       刀具编号
     * @param holderNumber 刀补号
     * @param compensation 补偿数据（Map，包含geom和wear）
     * @param updatedAt    更新时间戳（毫秒）
     * @param source       数据来源
     * @param traceId      追踪ID（可选）
     */
    public void saveTool(Long factoryId, Long deviceId, String toolNo, String holderNumber,
                         Map<String, Object> compensation, long updatedAt, String source, String traceId) {
        if (StringUtils.isBlank(toolNo) && StringUtils.isBlank(holderNumber) && 
            (compensation == null || compensation.isEmpty())) {
            return;
        }
        
        // 构建包含 toolNo、holderNumber 和 compensation 的完整结构
        Map<String, Object> toolData = new HashMap<>();
        if (StringUtils.isNotBlank(toolNo)) {
            toolData.put(DeviceToolEventFields.TOOL_NO, toolNo);
        }
        if (StringUtils.isNotBlank(holderNumber)) {
            toolData.put(DeviceToolEventFields.HOLDER_NUMBER, holderNumber);
        }
        if (compensation != null && !compensation.isEmpty()) {
            toolData.put(DeviceToolEventFields.COMPENSATION_FIELD, compensation);
        }
        
        // 添加元数据
        toolData.put(DeviceToolEventFields.UPDATED_AT, updatedAt);
        toolData.put(DeviceToolEventFields.SOURCE, source);
        if (StringUtils.isNotBlank(traceId)) {
            toolData.put(DeviceToolEventFields.TRACE_ID, traceId);
        }
        
        // 将整个结构序列化为JSON字符串，存储到Redis Hash的"data"字段
        try {
            String json = JsonUtils.toJsonString(toolData);
            String key = buildToolKey(factoryId, deviceId);
            Map<String, String> payload = new HashMap<>();
            payload.put("data", json);
            redisTemplate.opsForHash().putAll(key, payload);
            redisTemplate.expire(key, Duration.ofMillis(toolTtlMillis));
        } catch (Exception e) {
            log.warn("[DeviceToolCacheService] 保存刀具缓存失败: factoryId={}, deviceId={}, error={}",
                    factoryId, deviceId, e.getMessage());
        }
    }

    /**
     * 获取设备刀具缓存
     * <p>
     * 返回格式：包含 toolNo、holderNumber 和 compensation 的完整结构
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 刀具数据映射（包含 toolNo、holderNumber 和 compensation），如果不存在返回 null
     */
    public Map<String, Object> getTool(Long factoryId, Long deviceId) {
        String key = buildToolKey(factoryId, deviceId);
        Object dataObj = redisTemplate.opsForHash().get(key, "data");
        if (dataObj == null) {
            return null;
        }
        String json = dataObj.toString();
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[DeviceToolCacheService] 解析刀具缓存失败: factoryId={}, deviceId={}, error={}",
                    factoryId, deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取刀具编号
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 刀具编号，如果不存在返回 null
     */
    public String getToolNumber(Long factoryId, Long deviceId) {
        String key = buildToolKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, DeviceToolEventFields.TOOL_NO);
        return String.valueOf(value);
    }

    // ==================== 刀补补偿缓存 ====================

    /**
     * /**
     * 获取当前有效的刀补补偿值（从缓存）
     * <p>
     * 用于快速判断补偿值是否变化，减少数据库查询
     * </p>
     *
     * @param deviceId     设备ID
     * @param holderNumber 刀补号
     * @return 补偿值（Map），如果缓存不存在返回 null
     */
    public Map<String, Object> getActiveCompensation(Long deviceId, String holderNumber) {
        if (StringUtils.isAnyBlank(holderNumber) || deviceId == null) {
            return null;
        }
        String key = buildCompensationKey(deviceId);
        Object jsonObj = redisTemplate.opsForHash().get(key, holderNumber);
        if (jsonObj == null) {
            return null;
        }
        String json = jsonObj.toString();
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[DeviceToolCacheService] 解析刀补补偿缓存失败: deviceId={}, holderNumber={}, error={}",
                    deviceId, holderNumber, e.getMessage());
            // 缓存数据损坏，删除该field
            redisTemplate.opsForHash().delete(key, holderNumber);
            return null;
        }
    }

    /**
     * 缓存当前有效的刀补补偿值
     * <p>
     * 在写入数据库后同步更新缓存，用于后续快速判断补偿值是否变化
     * </p>
     *
     * @param deviceId     设备ID
     * @param holderNumber 刀补号
     * @param compValue    补偿值（Map）
     */
    public void cacheActiveCompensation(Long deviceId, String holderNumber, Map<String, Object> compValue) {
        if (deviceId == null || StringUtils.isAnyBlank(holderNumber) || compValue == null) {

            return;
        }
        try {
            String key = buildCompensationKey(deviceId);
            String json = JsonUtils.toJsonString(compValue);
            redisTemplate.opsForHash().put(key, holderNumber, json);
            redisTemplate.expire(key, Duration.ofSeconds(compensationCacheTtlSeconds));
            log.debug("[DeviceToolCacheService] 缓存刀补补偿值: deviceId={}, holderNumber={}", deviceId, holderNumber);
        } catch (Exception e) {
            log.warn("[DeviceToolCacheService] 缓存刀补补偿值失败: deviceId={}, holderNumber={}, error={}",
                    deviceId, holderNumber, e.getMessage());
        }
    }

    /**
     * 删除刀补补偿缓存
     * <p>
     * 当补偿记录被关闭时，删除缓存（下次查询会从数据库重新加载）
     * </p>
     *
     * @param deviceId     设备ID
     * @param holderNumber 刀补号
     */
    public void deleteActiveCompensation(Long deviceId, String holderNumber) {
        if (deviceId == null || StringUtils.isAnyBlank(holderNumber)) {
            return;
        }
        String key = buildCompensationKey(deviceId);
        redisTemplate.opsForHash().delete(key, holderNumber);
        log.debug("[DeviceToolCacheService] 删除刀补补偿缓存: deviceId={}, holderNumber={}", deviceId, holderNumber);
    }

    /**
     * 构建刀补补偿缓存键（Hash结构，按设备ID分组）
     *
     * @param deviceId 设备ID
     * @return Redis 键
     */
    private String buildCompensationKey(Long deviceId) {
        return String.format(RedisConstant.COMPENSATION_ACTIVE, deviceId);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建刀具缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildToolKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_TOOL,
                factoryId, deviceId);
    }
}




