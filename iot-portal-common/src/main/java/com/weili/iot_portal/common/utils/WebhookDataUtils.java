package com.weili.iot_portal.common.utils;

import java.util.Map;

/**
 * Webhook 数据处理工具类
 * <p>
 * 用于从 Webhook 请求的数据结构中提取和转换数据。
 * </p>
 * 
 * @author system
 */
public class WebhookDataUtils {

    /**
     * 从 Map 中获取字符串值
     * <p>
     * 安全地从 Map 中提取字符串值，处理 null 值情况。
     * 如果值为 null，返回 null；否则调用 toString() 方法。
     * </p>
     * 
     * @param map Map 数据
     * @param key 键名
     * @return 字符串值，如果不存在或为 null 则返回 null
     */
    public static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 将对象转换为字符串
     * <p>
     * 安全地将对象转换为字符串，处理 null 值情况。
     * </p>
     * 
     * @param value 对象值
     * @return 字符串值，如果为 null 则返回 null
     */
    public static String toString(Object value) {
        return value == null ? null : value.toString();
    }
}

