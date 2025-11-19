package com.weili.iot_portal.business.device_mgmt.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@RequiredArgsConstructor
public enum DeviceMetricType {

    OEE("oee", "OEE", "%"),
    TIME_AVAILABILITY("timeAvailability", "时间开动率", "%"),
    PERFORMANCE_RATE("performanceRate", "性能开动率", "%"),
    EQUIPMENT_AVAILABILITY("equipmentAvailability", "设备开动率", "%"),
    DOWNTIME_RATE("downtimeRate", "停机率", "%");

    private static final Map<String, DeviceMetricType> CACHE = new ConcurrentHashMap<>();

    static {
        Arrays.stream(values()).forEach(type -> CACHE.put(type.code, type));
    }

    public static DeviceMetricType of(String code) {
        if (code == null) {
            return null;
        }
        return CACHE.get(code);
    }

    private final String code;
    private final String displayName;
    private final String unit;
}

