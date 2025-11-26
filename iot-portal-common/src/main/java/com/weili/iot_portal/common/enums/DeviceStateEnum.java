package com.weili.iot_portal.common.enums;

/**
 * 设备状态枚举
 */
public enum DeviceStateEnum {

    RUNNING,
    WORKING,
    STANDBY,
    FAULT,
    SHUTDOWN,
    IDLE,
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


