package com.weili.iot_portal.service.cache;

import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceAxisEventFields;
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
 * 设备轴数据缓存服务
 * 轴坐标数据 (Hash结构)
 *{
 *   "X.abs": "1.00",
 *   "X.rel": "0.31",
 *   "X.mach": "1.00",
 *   "X.rem": "0.00",
 *   "Y.abs": "1.00",
 *   "Y.rel": "1.00",
 *   "Y.mach": "1.00",
 *   "Y.rem": "1.00",
 *   ... ( Z, A等其他轴)
 *   "ratio": "50",              // 倍率值
 *   "updatedAt": "1731470400000",
 *   "source": "TB",
 *   "traceId": "msg-xxx"
 * }
 * 曲线数据 (List结构)
 * [
 *   "1731470400000:48",  // 最新
 *   "1731470401000:52",
 *   "1731470400000:50"   // 最旧
 * ]
 * <p>
 * 优化：使用Caffeine缓存自动清理过期条目，避免内存泄漏
 * </p>
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAxisCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RealtimeCacheSampler sampler;
    private final RealtimeCacheWriter writer;

    @Value("${rt.axis.ttl-millis:300000}")
    private long axisTtlMillis;

    @Value("${rt.axis.curve.ttl-millis:300000}")
    private long axisCurveTtlMillis;

    /**
     * 采样写入间隔（毫秒）
     * Apollo配置：rt.axis.sample-interval-millis
     * 默认值：10000（10秒）
     */
    @Value("${rt.axis.sample-interval-millis:10000}")
    private long sampleIntervalMillis;

    // ==================== 轴数据缓存 ====================

    /**
     * 保存或更新设备轴数据缓存
     * <p>
     * 优化：采样写入（每10秒写入一次，其余数据丢弃）
     * - 减少Redis写入压力
     * - 适用于高频实时数据
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param axisData  轴数据映射（key 为字段名，value 为字段值）
     * @param updatedAt 更新时间戳（毫秒）
     * @param source    数据来源
     * @param traceId   追踪ID（可选）
     * @param ratio     倍率值（可选）
     */
    public void saveAxisData(Long factoryId, Long deviceId, Map<String, Object> axisData,
                             long updatedAt, String source, String traceId, Object ratio) {
        if (axisData == null || axisData.isEmpty()) {
            return;
        }
        
        String key = buildAxisKey(factoryId, deviceId);
        long now = System.currentTimeMillis();
        
        // 构建payload
        Map<String, String> payload = new HashMap<>();
        axisData.forEach((k, v) -> payload.put(k, String.valueOf(v)));
        payload.put(DeviceAxisEventFields.UPDATED_AT, String.valueOf(updatedAt));
        payload.put(DeviceAxisEventFields.SOURCE, source);
        if (StringUtils.isNotBlank(traceId)) {
            payload.put(DeviceAxisEventFields.TRACE_ID, traceId);
        }
        if (ratio != null) {
            payload.put(DeviceAxisEventFields.RATIO, String.valueOf(ratio));
        }
        
        // 使用工具类进行采样写入（使用sampler内部的Caffeine缓存）
        boolean written = writer.writeHashWithSampling(
                factoryId, deviceId, key, payload, axisTtlMillis,
                now, sampleIntervalMillis, sampler);
        
        if (!written) {
            // 被采样过滤，未写入（正常情况，不记录日志）
            return;
        }
    }

    /**
     * 获取设备轴数据缓存
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return 轴数据映射，如果不存在返回 null
     */
    public Map<Object, Object> getAxisData(Long factoryId, Long deviceId) {
        String key = buildAxisKey(factoryId, deviceId);
        return redisTemplate.opsForHash().entries(key);
    }


    // ==================== 轴曲线数据缓存 ====================

    /**
     * 追加轴曲线数据点（优化版：使用紧凑格式）
     * <p>
     * 优化前：{"ts":1731470400000,"value":50} (32字节)
     * 优化后：1731470400000:50 (16字节)
     * 节省约50%内存 + JSON序列化开销
     * </p>
     * <p>
     * 采样写入：每10秒写入一次，其余数据丢弃
     * - 减少Redis写入压力
     * - 适用于高频实时数据
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param metric    指标名称（load/rpm/feed）
     * @param timestamp 时间戳（毫秒）
     * @param value     指标值
     * @param maxLen    最大长度
     */
    public void appendCurvePoint(Long factoryId, Long deviceId, String metric,
                                 long timestamp, Object value, int maxLen) {
        if (value == null) {
            return;
        }
        
        String key = buildCurveKey(factoryId, deviceId, metric);
        long now = System.currentTimeMillis();
        
        // 采样写入：检查是否需要写入（每10秒写入一次，考虑设备时间偏移）
        if (!sampler.shouldWriteWithOffset(factoryId, deviceId, key, now, sampleIntervalMillis)) {
            // 被采样过滤，未写入（正常情况，不记录日志）
            return;
        }
        
        String pointData = timestamp + ":" + value;
        
        // 使用工具类写入List数据
        writer.writeList(key, pointData, maxLen, axisCurveTtlMillis);
        
        // 更新最后写入时间（使用sampler内部的Caffeine缓存）
        sampler.updateLastWriteTime(key, now);
    }

    /**
     * 获取轴曲线数据
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param metric    指标名称
     * @param start     起始索引
     * @param end       结束索引（-1 表示全部）
     * @return 曲线数据点列表
     */
    public List<String> getCurvePoints(Long factoryId, Long deviceId,
                                       String metric, long start, long end) {
        String key = buildCurveKey(factoryId, deviceId, metric);
        return redisTemplate.opsForList().range(key, start, end);
    }


    // ==================== 辅助方法 ====================

    /**
     * 构建轴数据缓存键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @return Redis 键
     */
    private String buildAxisKey(Long factoryId, Long deviceId) {
        return String.format(RedisConstant.RT_AXIS, factoryId, deviceId);
    }

    /**
     * 构建轴曲线数据键
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param metric    指标名称
     * @return Redis 键
     */
    private String buildCurveKey(Long factoryId, Long deviceId, String metric) {
        return String.format(RedisConstant.RT_AXIS_CURVE,
                metric, factoryId, deviceId);
    }
}



