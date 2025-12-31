package com.weili.iot_portal.common.enums;

/**
 * @EnumName: AggregationType
 * @Description:
 * @Author: luying
 **/
public enum AggregationType {

    MINUTE(60),         // 按分钟聚合，5分钟=5个点
    TEN_SECONDS(10);    // 按10秒聚合，5分钟=30个点
    private final int intervalSeconds;

    AggregationType(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public static AggregationType fromString(String type) {
        if (type == null) {
            return MINUTE;  // 默认按分钟聚合
        }
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MINUTE;
        }
    }
}
