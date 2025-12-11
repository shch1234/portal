package com.weili.iot_portal.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Webhook 时间戳提取工具类
 * <p>
 * 用于从 Webhook 请求的各种数据源中提取设备原始时间戳。
 * 支持从 eventData、telemetryData 等 Map 结构中提取时间戳。
 * </p>
 * 
 * @author system
 */
@Slf4j
public class WebhookTimestampUtils {

    /**
     * 时间戳字段名：timestamp（优先级最高）
     */
    public static final String TIMESTAMP = "timestamp";

    /**
     * 时间戳字段名：ts（优先级次之）
     */
    public static final String TS = "ts";

    /**
     * 时间戳字段名：dataTimestamp（优先级最低）
     */
    public static final String DATA_TIMESTAMP = "dataTimestamp";

    /**
     * 从 Map 中提取时间戳（毫秒）
     * <p>
     * 支持多种时间戳字段名，按优先级提取：
     * 1. timestamp（优先级最高）
     * 2. ts（优先级次之）
     * 3. dataTimestamp（优先级最低）
     * </p>
     * <p>
     * 支持的时间戳格式：
     * - 数字类型（Long, Integer, Double 等）
     * - 字符串类型（可解析为 Long 的字符串）
     * </p>
     * 
     * @param data Map 数据（可以是 eventData、telemetryData 等）
     * @return 时间戳（毫秒），如果不存在或解析失败返回 null
     */
    public static Long extractTimestamp(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        // 按优先级尝试提取时间戳字段
        Object timestampObj = data.get(TIMESTAMP);
        if (timestampObj == null) {
            timestampObj = data.get(TS);
        }
        if (timestampObj == null) {
            timestampObj = data.get(DATA_TIMESTAMP);
        }

        if (timestampObj == null) {
            return null;
        }

        try {
            // 支持字符串和数字格式的时间戳
            if (timestampObj instanceof String) {
                return Long.parseLong((String) timestampObj);
            } else if (timestampObj instanceof Number) {
                return ((Number) timestampObj).longValue();
            }
        } catch (Exception e) {
            log.warn("[WebhookTimestampUtils] 提取时间戳失败: timestamp={}, error={}",
                    timestampObj, e.getMessage());
        }

        return null;
    }

    /**
     * 从 WebhookRequest 中提取设备原始时间戳（毫秒）
     * <p>
     * 提取优先级：
     * 1. eventData.timestamp/ts/dataTimestamp（设备实际状态变化时间，最准确）
     * 2. telemetryData.timestamp/ts/dataTimestamp（TB从设备telemetry中提取的时间戳）
     * 3. request.dataTimestamp（TB数据时间戳）
     * 4. request.timestamp（TB事件时间戳）
     * </p>
     * <p>
     * 这样可以保证即使 portal 和 TB 连接断开，重新连接后数据仍然正确。
     * </p>
     * 
     * @param eventData 事件数据
     * @param telemetryData 遥测数据
     * @param dataTimestamp TB数据时间戳
     * @param timestamp TB事件时间戳
     * @return 时间戳（毫秒），如果不存在返回 null
     */
    public static Long extractDeviceTimestamp(Map<String, Object> eventData,
                                               Map<String, Object> telemetryData,
                                               Long dataTimestamp,
                                               Long timestamp) {
        // 优先级1：从 eventData 中提取
        Long eventDataTs = extractTimestamp(eventData);
        if (eventDataTs != null) {
            return eventDataTs;
        }

        // 优先级2：从 telemetryData 中提取
        Long telemetryDataTs = extractTimestamp(telemetryData);
        if (telemetryDataTs != null) {
            return telemetryDataTs;
        }

        // 优先级3：使用 TB 数据时间戳
        if (dataTimestamp != null) {
            return dataTimestamp;
        }

        // 优先级4：使用 TB 事件时间戳
        if (timestamp != null) {
            return timestamp;
        }

        return null;
    }
}

