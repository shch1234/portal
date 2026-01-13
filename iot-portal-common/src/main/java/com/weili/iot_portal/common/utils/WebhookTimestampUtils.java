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
     * 提取优先级（优先使用设备上传的时间戳，而不是系统时间）：
     * 1. eventData.timestamp/ts/dataTimestamp（事件数据中的时间戳，设备上传的，对于有延时的事件是T1时刻）
     * 2. telemetryData.timestamp/ts/dataTimestamp（遥测数据时间戳，设备上传的）
     * 3. request.timestamp（TB事件时间戳，TB端从设备数据中提取的，设备时间戳）
     * 4. request.dataTimestamp（TB数据时间戳，TB端从设备数据中提取的，设备时间戳）
     * </p>
     * <p>
     * 原则：优先使用设备上传的时间戳，而不是系统时间。
     * - eventData.timestamp 和 telemetryData.timestamp 是直接从设备上传的数据中提取的
     * - request.timestamp 和 request.dataTimestamp 是TB端从设备数据中提取的（不是系统时间）
     * - 只有在所有字段都为空时，才会返回null（调用方应处理null情况）
     * </p>
     * <p>
     * 注意：对于有延时的事件（如加工开始，有minDurationMs），TB端会确保：
     * - eventData.timestamp = T1时刻的设备时间戳（最初检测到状态变化时的设备时间戳）
     * - request.timestamp = T1时刻的设备时间戳
     * - request.dataTimestamp = T1时刻的设备时间戳
     * - telemetryData.timestamp 可能是T2时刻（确认时刻）的遥测数据时间戳
     * </p>
     * 
     * @param eventData 事件数据
     * @param telemetryData 遥测数据
     * @param dataTimestamp TB数据时间戳（TB端从设备数据中提取的）
     * @param timestamp TB事件时间戳（TB端从设备数据中提取的）
     * @return 时间戳（毫秒），如果不存在返回 null
     */
    public static Long extractDeviceTimestamp(Map<String, Object> eventData,
                                               Map<String, Object> telemetryData,
                                               Long dataTimestamp,
                                               Long timestamp) {
        // 优先级1：从 eventData 中提取（设备上传的时间戳，对于有延时的事件是T1时刻）
        Long eventDataTs = extractTimestamp(eventData);
        if (eventDataTs != null) {
            return eventDataTs;
        }

        // 优先级2：从 telemetryData 中提取（设备上传的时间戳）
        Long telemetryDataTs = extractTimestamp(telemetryData);
        if (telemetryDataTs != null) {
            return telemetryDataTs;
        }

        // 优先级3：使用 TB 事件时间戳（TB端从设备数据中提取的，设备时间戳）
        if (timestamp != null) {
            return timestamp;
        }

        // 优先级4：使用 TB 数据时间戳（TB端从设备数据中提取的，设备时间戳）
        if (dataTimestamp != null) {
            return dataTimestamp;
        }

        return null;
    }
}

