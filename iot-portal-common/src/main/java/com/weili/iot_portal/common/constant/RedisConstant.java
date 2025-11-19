package com.weili.iot_portal.common.constant;

/**
 * @author luying
 * @className RedisConstant
 * @description
 * @date 2025-11-17 11:26
 **/
public class RedisConstant {

    public static final String OAUTH2_ACCESS_TOKEN = "iot_portal:access_token:%s";
    public static final String DEVICE_FACTORY = "iot_portal:device:factory:%s";
    public static final String DEVICE_CODE_IDENTITY = "iot_portal:device:code:%s";
    public static final String UNKNOWN_DEVICE_ALERT = "iot_portal:unknown_device:%s:%s:%s";

    private RedisConstant() {
    }
}
