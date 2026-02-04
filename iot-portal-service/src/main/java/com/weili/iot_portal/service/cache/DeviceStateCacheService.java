package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态实时缓存服务
 * <p>
 * 设备当前运行的状态和心跳
 * </p>
 * {
 *   "state": "0",
 *   "updatedAt": "1731470400000",
 *   "source": "TB",
 *   "traceId": "msg-abc123"
 * }
 * <p>
 * 优化：使用Caffeine缓存自动清理过期条目，避免内存泄漏
 * </p>
 * @author luying
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SafeRedisOperations safeRedisOperations;
    private final ResourceLimiter resourceLimiter;
    private final RealtimeCacheSampler sampler;
    private final RealtimeCacheWriter writer;

    @Value("${rt.state.ttl-millis:600000}")
    private long stateTtlMillis;

    @Value("${rt.state.heartbeat-ttl-seconds:120}")
    private long stateHeartbeatTtlSeconds;

    /**
     * Pipeline批量大小限制
     * Apollo配置：spring.data.redis.lettuce.pipeline.batch-size
     * 默认值：20（每批最多20个设备，超过则分批执行）
     * 优化：从50降低到20，减少Pipeline结果的内存占用，避免OOM
     */
    @Value("${spring.data.redis.lettuce.pipeline.batch-size:20}")
    private int pipelineBatchSize;
    
    /**
     * 单个Hash的最大字段数限制（防止单个Hash过大导致内存溢出）
     * Apollo配置：spring.data.redis.lettuce.hash.max-fields
     * 默认值：100（如果Hash字段数超过100，只取前100个）
     */
    @Value("${spring.data.redis.lettuce.hash.max-fields:100}")
    private int maxHashFields;

    /**
     * 采样写入间隔（毫秒）
     * Apollo配置：rt.state.sample-interval-millis
     * 默认值：10000（10秒）
     */
    @Value("${rt.state.sample-interval-millis:10000}")
    private long sampleIntervalMillis;

    // ==================== 状态数据缓存 ====================

    /**
     * 保存或更新设备状态缓存
     * <p>
     * 优化：采样写入（每10秒写入一次，其余数据丢弃）
     * - 减少Redis写入压力
     * - 适用于高频实时数据（如心跳等）
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param state     状态值
     * @param updatedAt 更新时间戳（毫秒）
     * @param source    数据来源
     * @param traceId   追踪ID（可选）
     */
    public void saveState(Long factoryId, Long deviceId, String state,
                          long updatedAt, String source, String traceId) {
        String key = buildStateKey(factoryId, deviceId);
        long now = System.currentTimeMillis();
        
        // 构建payload
        Map<String, String> payload = new HashMap<>();
        payload.put(DeviceStateEventFields.STATE, state);
        payload.put(DeviceStateEventFields.UPDATED_AT, String.valueOf(updatedAt));
        payload.put(DeviceStateEventFields.SOURCE, source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put(DeviceStateEventFields.TRACE_ID, traceId);
        }
        
        // 使用工具类进行采样写入（使用sampler内部的Caffeine缓存）
        boolean written = writer.writeHashWithSampling(
                factoryId, deviceId, key, payload, stateTtlMillis,
                now, sampleIntervalMillis, sampler);
        
        if (!written) {
            // 被采样过滤，未写入（正常情况，不记录日志）
            return;
        }
    }

    /**
     * 批量保存设备状态缓存
     * <p>
     * 优化：使用Pipeline批量写入，减少网络往返，提高性能
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceStates 设备状态数据映射，Key: deviceId, Value: 状态数据
     */
    public void batchSaveState(Long factoryId, Map<Long, DeviceStateData> deviceStates) {
        if (deviceStates == null || deviceStates.isEmpty()) {
            return;
        }

        // 如果设备数量小于等于批量大小，直接执行
        if (deviceStates.size() <= pipelineBatchSize) {
            batchSaveStateInternal(factoryId, deviceStates);
            return;
        }

        // 分批处理
        List<Map.Entry<Long, DeviceStateData>> entries = new java.util.ArrayList<>(deviceStates.entrySet());
        for (int i = 0; i < entries.size(); i += pipelineBatchSize) {
            int end = Math.min(i + pipelineBatchSize, entries.size());
            Map<Long, DeviceStateData> batch = new HashMap<>();
            for (int j = i; j < end; j++) {
                Map.Entry<Long, DeviceStateData> entry = entries.get(j);
                batch.put(entry.getKey(), entry.getValue());
            }
            batchSaveStateInternal(factoryId, batch);
        }
    }

    /**
     * 内部方法：批量保存设备状态缓存（单批，使用Pipeline）
     *
     * @param factoryId 工厂ID
     * @param deviceStates 设备状态数据映射（不超过pipelineBatchSize）
     */
    private void batchSaveStateInternal(Long factoryId, Map<Long, DeviceStateData> deviceStates) {
        if (deviceStates == null || deviceStates.isEmpty()) {
            return;
        }

        // 尝试获取Redis Pipeline许可（限流保护）
        if (!resourceLimiter.tryAcquireRedisPipeline()) {
            log.warn("[DeviceStateCache] 获取Redis Pipeline许可失败，降级为单个写入: factoryId={}, deviceCount={}",
                    factoryId, deviceStates.size());
            // 降级为单个写入
            deviceStates.forEach((deviceId, data) ->
                    saveState(factoryId, deviceId, data.getState(), data.getUpdatedAt(), data.getSource(), data.getTraceId()));
            return;
        }

        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (Map.Entry<Long, DeviceStateData> entry : deviceStates.entrySet()) {
                    Long deviceId = entry.getKey();
                    DeviceStateData data = entry.getValue();

                    Map<String, String> payload = new HashMap<>();
                    payload.put(DeviceStateEventFields.STATE, data.getState());
                    payload.put(DeviceStateEventFields.UPDATED_AT, String.valueOf(data.getUpdatedAt()));
                    payload.put(DeviceStateEventFields.SOURCE, data.getSource());
                    if (StringUtils.isNotBlank(data.getTraceId())) {
                        payload.put(DeviceStateEventFields.TRACE_ID, data.getTraceId());
                    }

                    String key = buildStateKey(factoryId, deviceId);
                    byte[] keyBytes = key.getBytes();

                    // 序列化Map为字节数组（Hash结构）
                    Map<byte[], byte[]> hashData = new HashMap<>();
                    for (Map.Entry<String, String> field : payload.entrySet()) {
                        hashData.put(field.getKey().getBytes(), field.getValue().getBytes());
                    }
                    connection.hashCommands().hMSet(keyBytes, hashData);
                    connection.expire(keyBytes, stateTtlMillis / 1000);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[DeviceStateCache] 批量保存设备状态失败: factoryId={}, deviceCount={}",
                    factoryId, deviceStates.size(), e);
            // 降级为单个写入
            deviceStates.forEach((deviceId, data) -> {
                try {
                    saveState(factoryId, deviceId, data.getState(), data.getUpdatedAt(), data.getSource(), data.getTraceId());
                } catch (Exception ex) {
                    log.warn("[DeviceStateCache] 单个保存设备状态失败: deviceId={}", deviceId, ex);
                }
            });
        } finally {
            // 释放Redis Pipeline许可
            resourceLimiter.releaseRedisPipeline();
        }
    }

    /**
     * 设备状态数据
     */
    public static class DeviceStateData {
        private String state;
        private long updatedAt;
        private String source;
        private String traceId;

        public DeviceStateData(String state, long updatedAt, String source, String traceId) {
            this.state = state;
            this.updatedAt = updatedAt;
            this.source = source;
            this.traceId = traceId;
        }

        public String getState() { return state; }
        public long getUpdatedAt() { return updatedAt; }
        public String getSource() { return source; }
        public String getTraceId() { return traceId; }
    }

    /**
     * 获取设备状态值
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 状态值，如果不存在返回 null
     */
    public String getStateValue(Long factoryId, Long deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        String value = safeRedisOperations.safeHGet(key, DeviceStateEventFields.STATE);
        return value;
    }

    /**
     * 批量获取设备状态值（只获取state字段，优化内存使用）
     * <p>
     * 优化：
     * 1. 只获取state字段，而不是整个Hash（减少90%+内存占用）
     * 2. Pipeline批量大小限制，超过限制则分批执行
     * 3. 使用限流器控制Pipeline并发，避免连接池耗尽
     * 4. 添加重试机制，处理连接池异常
     * 5. 降级策略：Pipeline失败时降级为逐个读取
     * </p>
     * <p>
     * 适用场景：只需要state字段的场景（如Dashboard统计）
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表
     * @return 设备ID到状态值的映射，设备ID -> state值
     */
    public Map<Long, String> batchGetStateValue(Long factoryId, List<Long> deviceIds) {
        Map<Long, String> result = new HashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }

        // 如果设备数量小于等于批量大小，直接执行
        if (deviceIds.size() <= pipelineBatchSize) {
            return batchGetStateValueInternal(factoryId, deviceIds);
        }

        // 分批处理
        for (int i = 0; i < deviceIds.size(); i += pipelineBatchSize) {
            int end = Math.min(i + pipelineBatchSize, deviceIds.size());
            List<Long> batch = deviceIds.subList(i, end);
            Map<Long, String> batchResult = batchGetStateValueInternal(factoryId, batch);
            result.putAll(batchResult);
        }

        return result;
    }

    /**
     * 内部方法：批量获取设备状态值（单批，只获取state字段）
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表（不超过pipelineBatchSize）
     * @return 设备ID到状态值的映射
     */
    private Map<Long, String> batchGetStateValueInternal(Long factoryId, List<Long> deviceIds) {
        Map<Long, String> result = new HashMap<>(deviceIds.size());

        // 尝试获取Redis Pipeline许可（限流保护）
        if (!resourceLimiter.tryAcquireRedisPipeline()) {
            log.warn("[DeviceStateCache] 获取Redis Pipeline许可失败，降级为逐个读取: factoryId={}, deviceIds size={}",
                    factoryId, deviceIds.size());
            return fallbackToIndividualStateValueRead(factoryId, deviceIds);
        }

        try {
            int maxRetries = 3;
            long retryDelayMs = 100; // 初始延迟100ms

            for (int retry = 0; retry <= maxRetries; retry++) {
                try {
                    // 优化：只获取state字段，而不是整个Hash（hGet而不是hGetAll）
                    List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (Long deviceId : deviceIds) {
                            String key = buildStateKey(factoryId, deviceId);
                            byte[] keyBytes = key.getBytes();
                            byte[] fieldBytes = DeviceStateEventFields.STATE.getBytes();
                            // 只获取state字段，减少内存占用
                            connection.hashCommands().hGet(keyBytes, fieldBytes);
                        }
                        return null;
                    });

                    // 组装结果（只包含state值，内存占用大幅降低）
                    for (int i = 0; i < deviceIds.size() && i < pipelineResults.size(); i++) {
                        Long deviceId = deviceIds.get(i);
                        Object pipelineResult = pipelineResults.get(i);

                        if (pipelineResult != null) {
                            String stateValue = pipelineResult.toString();
                            if (StringUtils.isNotBlank(stateValue)) {
                                result.put(deviceId, stateValue);
                            }
                        }
                    }
                    return result; // 成功，返回结果
                } catch (org.springframework.dao.QueryTimeoutException e) {
                    // ⚠️ Redis 超时异常：连接已自动释放，但需要处理业务逻辑
                    // 超时异常通常不可重试，直接降级
                    log.warn("[DeviceStateCache] Pipeline操作超时，降级为逐个读取: factoryId={}, deviceIds size={}, error={}",
                            factoryId, deviceIds.size(), e.getMessage());
                    break;
                } catch (RedisConnectionFailureException e) {
                    // 连接池异常，判断是否需要重试
                    if (retry < maxRetries && isRetryableException(e)) {
                        log.warn("[DeviceStateCache] Pipeline失败，重试 {}/{}: factoryId={}, deviceIds size={}",
                                retry + 1, maxRetries, factoryId, deviceIds.size());
                        try {
                            Thread.sleep(retryDelayMs * (1L << retry)); // 指数退避
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    // 重试次数用完或不可重试异常，降级为逐个读取
                    log.error("[DeviceStateCache] Pipeline失败，降级为逐个读取: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                } catch (Exception e) {
                    // 其他异常，降级为逐个读取
                    log.error("[DeviceStateCache] 批量获取设备状态值失败: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                }
            }

            // 所有重试都失败，降级为逐个读取
            return fallbackToIndividualStateValueRead(factoryId, deviceIds);
        } finally {
            // 释放Redis Pipeline许可
            resourceLimiter.releaseRedisPipeline();
        }
    }

    /**
     * 降级方案：逐个读取设备状态值
     */
    private Map<Long, String> fallbackToIndividualStateValueRead(Long factoryId, List<Long> deviceIds) {
        Map<Long, String> result = new HashMap<>(deviceIds.size());
        for (Long deviceId : deviceIds) {
            try {
                String stateValue = getStateValue(factoryId, deviceId);
                if (stateValue != null) {
                    result.put(deviceId, stateValue);
                }
            } catch (Exception e) {
                log.warn("[DeviceStateCache] 单个读取设备状态值失败: deviceId={}", deviceId, e);
            }
        }
        return result;
    }

    /**
     * 批量获取设备状态（完整Hash，包含所有字段）
     * <p>
     * 优化：
     * 1. Pipeline批量大小限制，超过限制则分批执行
     * 2. 使用限流器控制Pipeline并发，避免连接池耗尽
     * 3. 添加重试机制，处理连接池异常
     * 4. 降级策略：Pipeline失败时降级为逐个读取
     * 5. Hash字段数限制，防止单个Hash过大导致内存溢出
     * </p>
     * <p>
     * 注意：此方法会获取整个Hash，内存占用较大。如果只需要state字段，请使用 {@link #batchGetStateValue}
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表
     * @return 设备ID到状态数据的映射，设备ID -> 状态数据映射（包含state, updatedAt等字段）
     */
    public Map<Long, Map<Object, Object>> batchGetState(Long factoryId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new HashMap<>();
        }

        // 如果设备数量小于等于批量大小，直接执行
        if (deviceIds.size() <= pipelineBatchSize) {
            return batchGetStateInternal(factoryId, deviceIds);
        }

        // 分批处理
        Map<Long, Map<Object, Object>> result = new HashMap<>(deviceIds.size());
        for (int i = 0; i < deviceIds.size(); i += pipelineBatchSize) {
            int end = Math.min(i + pipelineBatchSize, deviceIds.size());
            List<Long> batch = deviceIds.subList(i, end);
            Map<Long, Map<Object, Object>> batchResult = batchGetStateInternal(factoryId, batch);
            result.putAll(batchResult);
        }

        return result;
    }

    /**
     * 内部方法：批量获取设备状态（单批）
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表（不超过pipelineBatchSize）
     * @return 设备ID到状态数据的映射
     */
    private Map<Long, Map<Object, Object>> batchGetStateInternal(Long factoryId, List<Long> deviceIds) {
        Map<Long, Map<Object, Object>> result = new HashMap<>(deviceIds.size());

        // 尝试获取Redis Pipeline许可（限流保护）
        if (!resourceLimiter.tryAcquireRedisPipeline()) {
            log.warn("[DeviceStateCache] 获取Redis Pipeline许可失败，降级为逐个读取: factoryId={}, deviceIds size={}",
                    factoryId, deviceIds.size());
            return fallbackToIndividualStateRead(factoryId, deviceIds);
        }

        try {
            int maxRetries = 3;
            long retryDelayMs = 100; // 初始延迟100ms

            for (int retry = 0; retry <= maxRetries; retry++) {
                try {
                    // 使用Pipeline批量查询，减少网络往返
                    List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (Long deviceId : deviceIds) {
                            String key = buildStateKey(factoryId, deviceId);
                            byte[] keyBytes = key.getBytes();
                            connection.hashCommands().hGetAll(keyBytes);
                        }
                        return null;
                    });

                    // 组装结果（限制单个Hash的大小，防止内存溢出）
                    for (int i = 0; i < deviceIds.size() && i < pipelineResults.size(); i++) {
                        Long deviceId = deviceIds.get(i);
                        Object pipelineResult = pipelineResults.get(i);

                        if (pipelineResult instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<Object, Object> stateData = (Map<Object, Object>) pipelineResult;
                            if (!stateData.isEmpty()) {
                                // 优化：如果Hash字段数超过限制，只保留前N个字段，防止内存溢出
                                if (stateData.size() > maxHashFields) {
                                    log.warn("[DeviceStateCache] Hash字段数超过限制，截断: deviceId={}, fields={}, maxFields={}",
                                            deviceId, stateData.size(), maxHashFields);
                                    Map<Object, Object> truncatedData = new HashMap<>(maxHashFields);
                                    int count = 0;
                                    for (Map.Entry<Object, Object> entry : stateData.entrySet()) {
                                        if (count++ >= maxHashFields) {
                                            break;
                                        }
                                        truncatedData.put(entry.getKey(), entry.getValue());
                                    }
                                    result.put(deviceId, truncatedData);
                                } else {
                                    result.put(deviceId, stateData);
                                }
                            }
                        }
                    }
                    return result; // 成功，返回结果
                } catch (org.springframework.dao.QueryTimeoutException e) {
                    // ⚠️ Redis 超时异常：连接已自动释放，但需要处理业务逻辑
                    // 超时异常通常不可重试，直接降级
                    log.warn("[DeviceStateCache] Pipeline操作超时，降级为逐个读取: factoryId={}, deviceIds size={}, error={}",
                            factoryId, deviceIds.size(), e.getMessage());
                    break;
                } catch (RedisConnectionFailureException e) {
                    // 连接池异常，判断是否需要重试
                    if (retry < maxRetries && isRetryableException(e)) {
                        log.warn("[DeviceStateCache] Pipeline失败，重试 {}/{}: factoryId={}, deviceIds size={}",
                                retry + 1, maxRetries, factoryId, deviceIds.size());
                        try {
                            Thread.sleep(retryDelayMs * (1L << retry)); // 指数退避
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    // 重试次数用完或不可重试异常，降级为逐个读取
                    log.error("[DeviceStateCache] Pipeline失败，降级为逐个读取: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                } catch (Exception e) {
                    // 其他异常，降级为逐个读取
                    log.error("[DeviceStateCache] 批量获取设备状态失败: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                }
            }
        } finally {
            // 释放Redis Pipeline许可
            resourceLimiter.releaseRedisPipeline();
        }

        // 降级为逐个读取
        return fallbackToIndividualStateRead(factoryId, deviceIds);
    }

    /**
     * 降级为逐个读取设备状态
     */
    private Map<Long, Map<Object, Object>> fallbackToIndividualStateRead(Long factoryId, List<Long> deviceIds) {
        Map<Long, Map<Object, Object>> result = new HashMap<>();
        for (Long deviceId : deviceIds) {
            try {
                String key = buildStateKey(factoryId, deviceId);
                Map<Object, Object> stateData = safeRedisOperations.safeHGetAll(key);
                if (stateData != null && !stateData.isEmpty()) {
                    result.put(deviceId, stateData);
                }
            } catch (Exception e) {
                log.warn("[DeviceStateCache] 逐个读取设备状态失败: deviceId={}", deviceId, e);
            }
        }
        return result;
    }

    /**
     * 刷新状态缓存 TTL（不修改值）
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     */
    public void refreshStateTtl(Long factoryId, Long deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        redisTemplate.expire(key, Duration.ofMillis(stateTtlMillis));
    }

    // ==================== 状态心跳缓存 ====================

    /**
     * 保存或刷新状态心跳
     * <p>
     * 优化：统一存储 "1" 作为心跳状态标记，忽略 traceId 参数
     * TTL 默认 300 秒（5分钟），可通过配置 rt.state.heartbeat-ttl-seconds 调整
     * </p>
     * <p>
     * TTL 设置建议：
     * - 120秒（2分钟）：适合高频发送场景（每10-30秒发送一次），实时性要求高，但可能因网络延迟误判
     * - 300秒（5分钟）：推荐值，平衡实时性和稳定性，适合大多数场景（每30秒-2分钟发送一次）
     * - 600秒（10分钟）：适合低频发送场景，但离线检测延迟较大
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param traceId   追踪ID（已废弃，统一存储 "1"）
     */
    public void saveHeartbeat(Long factoryId, Long deviceId, String traceId) {
        String key = buildHeartbeatKey(factoryId, deviceId);
        // 统一存储 "1" 作为心跳状态标记，忽略 traceId
        safeRedisOperations.safeSet(key, "1", Duration.ofSeconds(stateHeartbeatTtlSeconds));
    }

    /**
     * 获取状态心跳值
     * <p>
     * 优化：如果键不存在（已过期），返回 "0" 而不是 null
     * 返回值："1" 表示有心跳，"0" 表示无心跳（已过期）
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 心跳状态，"1" 表示有心跳，"0" 表示无心跳（已过期）
     */
    public String getHeartbeat(Long factoryId, Long deviceId) {
        String key = buildHeartbeatKey(factoryId, deviceId);
        String value = safeRedisOperations.safeGet(key);
        // 如果键不存在（已过期），返回 "0" 表示无心跳
        return value != null ? value : "0";
    }

    /**
     * 批量获取心跳状态
     * <p>
     * 优化：
     * 1. Pipeline批量大小限制，超过限制则分批执行
     * 2. 使用限流器控制Pipeline并发，避免连接池耗尽
     * 3. 添加重试机制，处理连接池异常
     * 4. 降级策略：Pipeline失败时所有设备返回"0"（无心跳）
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表
     * @return 设备ID到心跳状态的映射，设备ID -> "1"（有心跳）或"0"（无心跳）
     */
    public Map<Long, String> batchGetHeartbeatStatus(Long factoryId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new HashMap<>();
        }

        // 如果设备数量小于等于批量大小，直接执行
        if (deviceIds.size() <= pipelineBatchSize) {
            return batchGetHeartbeatStatusInternal(factoryId, deviceIds);
        }

        // 分批处理
        Map<Long, String> result = new HashMap<>(deviceIds.size());
        for (int i = 0; i < deviceIds.size(); i += pipelineBatchSize) {
            int end = Math.min(i + pipelineBatchSize, deviceIds.size());
            List<Long> batch = deviceIds.subList(i, end);
            Map<Long, String> batchResult = batchGetHeartbeatStatusInternal(factoryId, batch);
            result.putAll(batchResult);
        }

        return result;
    }

    /**
     * 内部方法：批量获取心跳状态（单批）
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表（不超过pipelineBatchSize）
     * @return 设备ID到心跳状态的映射
     */
    private Map<Long, String> batchGetHeartbeatStatusInternal(Long factoryId, List<Long> deviceIds) {
        Map<Long, String> result = new HashMap<>(deviceIds.size());

        // 尝试获取Redis Pipeline许可（限流保护）
        if (!resourceLimiter.tryAcquireRedisPipeline()) {
            log.warn("[DeviceStateCache] 获取Redis Pipeline许可失败，降级为默认值: factoryId={}, deviceIds size={}",
                    factoryId, deviceIds.size());
            return fallbackToDefaultHeartbeat(deviceIds);
        }

        try {
            int maxRetries = 3;
            long retryDelayMs = 100; // 初始延迟100ms

            for (int retry = 0; retry <= maxRetries; retry++) {
                try {
                    // 使用Pipeline批量查询，减少网络往返
                    List<Object> pipelineResults = redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        for (Long deviceId : deviceIds) {
                            String key = buildHeartbeatKey(factoryId, deviceId);
                            byte[] keyBytes = key.getBytes();
                            connection.stringCommands().get(keyBytes);
                        }
                        return null;
                    });

                    // 组装结果
                    for (int i = 0; i < deviceIds.size() && i < pipelineResults.size(); i++) {
                        Long deviceId = deviceIds.get(i);
                        Object pipelineResult = pipelineResults.get(i);

                        // 如果键不存在（已过期），返回"0"表示无心跳
                        String status = pipelineResult != null ? pipelineResult.toString() : "0";
                        result.put(deviceId, status);
                    }
                    return result; // 成功，返回结果
                } catch (RedisConnectionFailureException e) {
                    // 连接池异常，判断是否需要重试
                    if (retry < maxRetries && isRetryableException(e)) {
                        log.warn("[DeviceStateCache] Pipeline失败，重试 {}/{}: factoryId={}, deviceIds size={}",
                                retry + 1, maxRetries, factoryId, deviceIds.size());
                        try {
                            Thread.sleep(retryDelayMs * (1L << retry)); // 指数退避
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    // 重试次数用完或不可重试异常，降级为默认值
                    log.error("[DeviceStateCache] Pipeline失败，降级为默认值: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                } catch (Exception e) {
                    // 其他异常，降级为默认值
                    log.error("[DeviceStateCache] 批量获取心跳状态失败: factoryId={}, deviceIds size={}",
                            factoryId, deviceIds.size(), e);
                    break;
                }
            }
        } finally {
            // 释放Redis Pipeline许可
            resourceLimiter.releaseRedisPipeline();
        }

        // 降级为默认值（所有设备返回"0"）
        return fallbackToDefaultHeartbeat(deviceIds);
    }

    /**
     * 降级为默认心跳状态（所有设备返回"0"）
     */
    private Map<Long, String> fallbackToDefaultHeartbeat(List<Long> deviceIds) {
        Map<Long, String> result = new HashMap<>(deviceIds.size());
        for (Long deviceId : deviceIds) {
            result.put(deviceId, "0"); // 默认无心跳
        }
        return result;
    }

    /**
     * 判断是否为可重试的异常
     */
    private boolean isRetryableException(Exception e) {
        String errorMsg = e.getMessage();
        return errorMsg != null && (
                errorMsg.contains("Could not get a resource from the pool") ||
                errorMsg.contains("Timeout waiting for idle object") ||
                errorMsg.contains("Connection refused")
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建状态缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildStateKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_STATE, factoryId, deviceId);
    }

    /**
     * 构建状态心跳键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildHeartbeatKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_STATE_HEARTBEAT, factoryId, deviceId);
    }

}



