package com.weili.iot_portal.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 效率指标类型枚举
 * 
 * <p>对应需求：4.4 能效管理 - 5个指标
 * 每个指标独立，支持按指标查询所有设备在该班次的指标值
 */
@Getter
@RequiredArgsConstructor
public enum EfficiencyMetricType {

    /**
     * OEE - 综合设备效率
     */
    OEE("oee", "OEE", "%"),

    /**
     * 时间开动率
     */
    TIME_AVAILABILITY("timeAvailability", "时间开动率", "%"),

    /**
     * 性能开动率
     */
    PERFORMANCE_RATE("performanceRate", "性能开动率", "%"),

    /**
     * 设备开动率
     */
    EQUIPMENT_AVAILABILITY("equipmentAvailability", "设备开动率", "%"),

    /**
     * 停机率
     */
    DOWNTIME_RATE("downtimeRate", "停机率", "%");

    /**
     * 指标代码（对应device_metrics_shift.metrics中的key）
     */
    private final String code;

    /**
     * 指标显示名称
     */
    private final String displayName;

    /**
     * 指标单位
     */
    private final String unit;

    private static final Map<String, EfficiencyMetricType> CACHE = new ConcurrentHashMap<>();

    static {
        Arrays.stream(values()).forEach(type -> CACHE.put(type.code, type));
    }

    /**
     * 根据指标代码获取枚举
     */
    public static EfficiencyMetricType of(String code) {
        if (code == null) {
            return null;
        }
        return CACHE.get(code.toLowerCase());
    }
}

