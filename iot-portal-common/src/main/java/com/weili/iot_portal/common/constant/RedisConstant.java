package com.weili.iot_portal.common.constant;

/**
 * @author luying
 * @className RedisConstant
 * @description
 * @date 2025-11-17 11:26
 **/
public class RedisConstant {

    public static final String COMMON = "iot_portal:";
    public static final String DEVICE_FACTORY = "iot_portal:device:factory:%s";
    public static final String DEVICE_CODE_IDENTITY = "iot_portal:device:code:%s";
    public static final String DEVICE_INFO_MATCH = "iot_portal:device:info:%s";
    public static final String UNKNOWN_DEVICE_ALERT = "iot_portal:unknown_device:%s:%s";

    /**
     * 实时数据：设备当前状态（Hash）
     */
    public static final String RT_STATE = COMMON + "rt:state:%s:%s";
    /**
     * 实时数据：设备指标/数值（Hash）
     */
    public static final String RT_METRIC = COMMON + "rt:metric:%s:%s";
    /**
     * 实时数据：轴坐标（Hash）
     */
    public static final String RT_AXIS = COMMON + "rt:axis:%s:%s";
    /**
     * 实时数据：轴曲线数据（List，按指标名称分组，如 feed、rpm、load）
     */
    public static final String RT_AXIS_CURVE = COMMON + "rt:axis:curve:%s:%s:%s";
    /**
     * 实时数据：刀具信息（Hash）
     */
    public static final String RT_TOOL = COMMON + "rt:tool:%s:%s";
    /**
     * 实时数据：状态心跳（String）
     */
    public static final String RT_STATE_HEARTBEAT = COMMON + "rt:state:hb:%s:%s";
    /**
     * 实时数据：程序信息（Hash）
     */
    public static final String RT_PROGRAM = COMMON + "rt:program:%s:%s";
    /**
     * 实时数据：工厂级指标（Hash）
     */
    public static final String RT_FACTORY_METRIC = COMMON + "rt:factory_metric:%s";
    /**
     * 刀补补偿缓存：当前有效的补偿值（Hash，按设备ID分组，field为刀补号，value为JSON）
     */
    public static final String COMPENSATION_ACTIVE = COMMON + "compensation:active:%s";

    /**
     * 检查点服务：设备状态汇总检查点
     */
    public static final String CHECKPOINT_DEVICE_STATE_SUMMARY = COMMON + "device_state_summary:checkpoint:";
    /**
     * 检查点服务：设备指标计算检查点
     */
    public static final String CHECKPOINT_DEVICE_METRICS = COMMON + "device_metrics:checkpoint:";
    /**
     * 检查点服务：设备指标汇总检查点
     */
    public static final String CHECKPOINT_DEVICE_METRICS_SUMMARY = COMMON + "device_metrics_summary:checkpoint:";
    /**
     * 检查点服务：设备产量汇总检查点
     */
    public static final String CHECKPOINT_DEVICE_PRODUCTION_SUMMARY = COMMON + "device_production_summary:checkpoint:";
    /**
     * 检查点服务：工厂实时指标检查点
     */
    public static final String CHECKPOINT_FACTORY_METRICS = COMMON + "factory_metrics:checkpoint:";
    /**
     * 检查点服务：工厂班次指标汇总检查点
     */
    public static final String CHECKPOINT_FACTORY_METRICS_SUMMARY = COMMON + "factory_metrics_summary:checkpoint:";

    /**
     * Webhook 幂等性缓存键前缀
     */
    public static final String WEBHOOK_IDEMPOTENT = COMMON + "webhook:idempotent:";
    /**
     * Webhook 实时数据缓存键格式（String，按设备编码和事件类型）
     */
    public static final String WEBHOOK_REALTIME_DEVICE = COMMON + "realtime:device:%s:%s";

    /**
     * 设备状态锁键前缀
     */
    public static final String LOCK_KEY_PREFIX_STATE = COMMON + "state_lock:";
    /**
     * 设备告警锁键前缀
     */
    public static final String LOCK_KEY_PREFIX_ALARM = COMMON + "alarm_lock:";
    /**
     * 设备刀具变更锁键前缀
     */
    public static final String LOCK_KEY_PREFIX_TOOL_CHANGE = COMMON + "tool_lock:";

    /**
     * 设备加工状态锁键前缀
     */
    public static final String LOCK_KEY_PREFIX_PRODUCTION = COMMON + "production_lock:";
}
