package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
 * @author luying
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateCacheService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rt.state.ttl-millis:600000}")
    private long stateTtlMillis;

    @Value("${rt.state.heartbeat-ttl-seconds:120}")
    private long stateHeartbeatTtlSeconds;

    // ==================== 状态数据缓存 ====================

    /**
     * 保存或更新设备状态缓存
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
     * 获取设备状态值
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 状态值，如果不存在返回 null
     */
    public String getStateValue(Long factoryId, Long deviceId) {
        String key = buildStateKey(factoryId, deviceId);
        Object value = redisTemplate.opsForHash().get(key, DeviceStateEventFields.STATE);
        return value != null ? value.toString() : null;
    }

    /**
     * 批量获取设备状态
     *
     * @param factoryId 工厂ID
     * @param deviceIds 设备ID列表
     * @return 设备ID到状态数据的映射，设备ID -> 状态数据映射（包含state, updatedAt等字段）
     */
    public Map<Long, Map<Object, Object>> batchGetState(Long factoryId, List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Map<Object, Object>> result = new HashMap<>(deviceIds.size());

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

            // 组装结果
            for (int i = 0; i < deviceIds.size(); i++) {
                Long deviceId = deviceIds.get(i);
                Object pipelineResult = pipelineResults.get(i);

                if (pipelineResult instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> stateData = (Map<Object, Object>) pipelineResult;
                    if (!stateData.isEmpty()) {
                        result.put(deviceId, stateData);
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量获取设备状态失败, factoryId: {}, deviceIds size: {}", factoryId, deviceIds.size(), e);
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
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(stateHeartbeatTtlSeconds));
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
        String value = redisTemplate.opsForValue().get(key);
        // 如果键不存在（已过期），返回 "0" 表示无心跳
        return value != null ? value : "0";
    }

    /**
     * 批量获取心跳状态
     * <p>
     * 使用 Pipeline 批量查询，提高性能
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

        Map<Long, String> result = new HashMap<>(deviceIds.size());

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
            for (int i = 0; i < deviceIds.size(); i++) {
                Long deviceId = deviceIds.get(i);
                Object pipelineResult = pipelineResults.get(i);

                // 如果键不存在（已过期），返回"0"表示无心跳
                String status = pipelineResult != null ? pipelineResult.toString() : "0";
                result.put(deviceId, status);
            }
        } catch (Exception e) {
            log.error("批量获取心跳状态失败, factoryId: {}, deviceIds size: {}", factoryId, deviceIds.size(), e);
            // 发生异常时，所有设备默认返回"0"（无心跳）
            for (Long deviceId : deviceIds) {
                result.put(deviceId, "0");
            }
        }

        return result;
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



