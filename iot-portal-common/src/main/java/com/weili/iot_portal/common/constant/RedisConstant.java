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

    /** 实时数据：设备当前状态（Hash） */
    public static final String RT_STATE = "rt:state:%s:%s:%s";
    /** 实时数据：设备指标/数值（Hash） */
    public static final String RT_METRIC = "rt:metric:%s:%s:%s";
    /** 实时数据：设备事件摘要（List/Stream 可选） */
    public static final String RT_EVENT = "rt:event:%s:%s:%s";
    /** 实时数据：设备在线心跳（String） */
    public static final String RT_ONLINE = "rt:online:%s:%s:%s";
    /** 实时数据：轴坐标（Hash） */
    public static final String RT_AXIS = "rt:axis:%s:%s:%s";
    /** 实时数据：刀具信息（Hash） */
    public static final String RT_TOOL = "rt:tool:%s:%s:%s";
    /** 实时数据：状态心跳（String） */
    public static final String RT_STATE_HEARTBEAT = "rt:state:hb:%s:%s:%s";
    /** 实时数据：程序信息（Hash） */
    public static final String RT_PROGRAM = "rt:program:%s:%s:%s";

    private RedisConstant() {
    }
}
