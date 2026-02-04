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
 * <p>
 * 优化：使用Caffeine缓存自动清理过期条目，避免内存泄漏
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceToolCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SafeRedisOperations safeRedisOperations;
    private final RealtimeCacheSampler sampler;
    private final RealtimeCacheWriter writer;

    @Value("${rt.tool.ttl-millis:300000}")
    private long toolTtlMillis;

    /**
     * 刀补补偿缓存TTL（秒）
     * Apollo配置：compensation.cache.ttl-seconds
     * 默认值：3600（1小时）
     * <p>
     * 优化说明：
     * - 从24小时缩短到1小时，减少Redis内存占用（减少95%）
     * - 补偿值变化不频繁，1小时足够覆盖大部分查询场景
     * - 如果缓存过期，会从数据库重新加载，不影响业务
     * </p>
     */
    @Value("${compensation.cache.ttl-seconds:3600}")
    private long compensationCacheTtlSeconds;

    /**
     * 采样写入间隔（毫秒）
     * Apollo配置：rt.tool.sample-interval-millis
     * 默认值：10000（10秒）
     */
    @Value("${rt.tool.sample-interval-millis:10000}")
    private long sampleIntervalMillis;

    // ==================== 刀具数据缓存 ====================

    /**
     * 保存或更新设备刀具缓存
     * <p>
     * 存储格式：包含 toolNo、holderNumber 和 compensation 的完整JSON结构
     * </p>
     * <p>
     * 优化：采样写入（每10秒写入一次，其余数据丢弃）
     * - 减少Redis写入压力
     * - 适用于高频实时数据
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
        
        String key = buildToolKey(factoryId, deviceId);
        long now = System.currentTimeMillis();
        
        // 采样写入：检查是否需要写入（每10秒写入一次，考虑设备时间偏移）
        if (!sampler.shouldWriteWithOffset(factoryId, deviceId, key, now, sampleIntervalMillis)) {
            // 被采样过滤，未写入（正常情况，不记录日志）
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
            Map<String, String> payload = new HashMap<>();
            payload.put("data", json);
            
            // 使用工具类写入（不带采样，因为已经在上面检查过了）
            writer.writeHash(key, payload, toolTtlMillis);
            
            // 更新最后写入时间（使用sampler内部的Caffeine缓存）
            sampler.updateLastWriteTime(key, now);
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
        String json = safeRedisOperations.safeHGet(key, "data");
        if (json == null) {
            return null;
        }
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
     * <p>
     * 从缓存的 JSON 数据中提取 toolNo
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 刀具编号，如果不存在返回 null
     */
    public String getToolNo(Long factoryId, Long deviceId) {
        Map<String, Object> toolData = getTool(factoryId, deviceId);
        if (toolData == null) {
            return null;
        }
        Object toolNoObj = toolData.get(DeviceToolEventFields.TOOL_NO);
        return toolNoObj != null ? String.valueOf(toolNoObj) : null;
    }

    /**
     * 获取刀补号
     * <p>
     * 从缓存的 JSON 数据中提取 holderNumber
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 刀补号，如果不存在返回 null
     */
    public String getToolHolderNo(Long factoryId, Long deviceId) {
        Map<String, Object> toolData = getTool(factoryId, deviceId);
        if (toolData == null) {
            return null;
        }
        Object holderNoObj = toolData.get(DeviceToolEventFields.HOLDER_NUMBER);
        return holderNoObj != null ? String.valueOf(holderNoObj) : null;
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
        String json = safeRedisOperations.safeHGet(key, holderNumber);
        if (json == null) {
            return null;
        }
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("[DeviceToolCacheService] 解析刀补补偿缓存失败: deviceId={}, holderNumber={}, error={}",
                    deviceId, holderNumber, e.getMessage());
            // 缓存数据损坏，删除该field（使用 safeExecute 包装）
            safeRedisOperations.safeExecute(() -> {
                redisTemplate.opsForHash().delete(key, holderNumber);
                return true;
            }, () -> false);
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
            // 使用工具类统一处理 Redis 操作
            safeRedisOperations.safeExecute(() -> {
                safeRedisOperations.safeHSet(key, holderNumber, json);
                redisTemplate.expire(key, Duration.ofSeconds(compensationCacheTtlSeconds));
                return true;
            }, () -> false);
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
        // 使用 safeExecute 包装删除操作（工具类没有封装 Hash 字段删除）
        safeRedisOperations.safeExecute(() -> {
            redisTemplate.opsForHash().delete(key, holderNumber);
            return true;
        }, () -> false);
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




