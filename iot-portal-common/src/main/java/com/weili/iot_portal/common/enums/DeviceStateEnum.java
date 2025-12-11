package com.weili.iot_portal.common.enums;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 设备状态枚举
 */
public enum DeviceStateEnum {

    @JsonPropertyDescription("运行中")
    RUNNING,

    @JsonPropertyDescription("正常")
    WORKING,

    @JsonPropertyDescription("待机")
    STANDBY,

    @JsonPropertyDescription("故障")
    FAULT,

    @JsonPropertyDescription("关机")
    SHUTDOWN,

    @JsonPropertyDescription("空闲")
    IDLE,

    @JsonPropertyDescription("未知")
    UNKNOWN;

    public static DeviceStateEnum of(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String upper = value.toUpperCase();
        if ("WORKING".equals(upper)) {
            return WORKING;
        }
        if ("RUNNING".equals(upper)) {
            return RUNNING;
        }
        try {
            return DeviceStateEnum.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}


