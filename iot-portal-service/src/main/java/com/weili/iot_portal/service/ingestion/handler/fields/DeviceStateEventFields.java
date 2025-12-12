package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备状态事件字段常量定义
 * <p>
 * 用于 DEVICE_STATE 和 DEVICE_STATE_HEARTBEAT 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceStateEventFields {

    private DeviceStateEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型：设备状态事件
     */
    public static final String EVENT_TYPE = "DEVICE_STATE";

    /**
     * 事件类型：设备状态心跳事件
     */
    public static final String EVENT_TYPE_HEARTBEAT = "DEVICE_STATE_HEARTBEAT";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceStateEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceStateEvent";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceStateHeartbeat 处理器
     */
    public static final String EVENT_SOURCE_HEARTBEAT = "DeviceStateHeartbeat";

    // ==================== 事件数据字段 ====================
    /**
     * 上一个设备状态字段
     * 表示状态变更前的状态，如：WORKING、STANDBY、FAULT等
     */
    public static final String PREVIOUS_STATE = "previousState";

    /**
     * 当前设备状态字段（必填）
     * 表示状态变更后的当前状态
     */
    public static final String CURRENT_STATE = "currentState";

    /**
     * 事件数据中的时间戳字段（设备实际状态变化时间）
     * 优先级：TIMESTAMP > TS > DATA_TIMESTAMP
     */
    public static final String TIMESTAMP = "timestamp";
    
    /**
     * 事件数据中的时间戳字段（简化形式）
     */
    public static final String TS = "ts";
    
    /**
     * 事件数据中的时间戳字段（数据时间戳）
     */
    public static final String DATA_TIMESTAMP = "dataTimestamp";

    // ==================== 分布式锁相关 ====================

    /**
     * 分布式锁超时时间（秒）
     * 设备状态操作的锁超时时间，防止死锁
     */
    public static final long LOCK_TIMEOUT_SECONDS = 5L;

    /**
     * 分布式锁值
     * Redis 锁的占位值，表示锁已被占用
     */
    public static final String LOCK_VALUE = "1";

    // ==================== 实时缓存字段 ====================
    /**
     * 状态字段
     * 实时状态缓存中的状态值字段
     */
    public static final String STATE = "state";

    /**
     * 更新时间字段
     * 记录数据最后更新的时间戳（秒）
     */
    public static final String UPDATED_AT = "updatedAt";

    /**
     * 数据来源字段
     * 标识数据来源，通常为 "TB"（ThingsBoard）
     */
    public static final String SOURCE = "source";

    /**
     * 数据来源值：ThingsBoard
     */
    public static final String SOURCE_TB = "TB";

    /**
     * 追踪ID字段
     * 用于链路追踪，通常使用 messageId
     */
    public static final String TRACE_ID = "traceId";

    // ==================== 默认值 ====================
    /**
     * 默认空值占位符
     * 当工厂ID或设备ID为空时，使用此值作为 Redis Key 的占位符
     */
    public static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    /**
     * 默认心跳值
     * 当 traceId 为空时，使用此值作为心跳值
     */
    public static final String DEFAULT_HEARTBEAT_VALUE = "1";

    // ==================== 异常处理相关字段 ====================
    /**
     * 状态间隙原因
     * 用于标记状态间隙的原因
     */
    public static final String GAP_REASON = "gap_reason";

    /**
     * 期望的上一个状态
     * 事件中期望的上一个状态值
     */
    public static final String EXPECTED_PREVIOUS = "expected_previous";

    /**
     * 实际数据库状态
     * 数据库中实际存储的状态值
     */
    public static final String ACTUAL_DB_STATE = "actual_db_state";

    /**
     * 状态不匹配原因
     * 用于标记状态不匹配的原因
     */
    public static final String MISMATCH_REASON = "mismatch_reason";

    /**
     * 期望状态
     * 期望的状态值
     */
    public static final String EXPECTED = "expected";

    /**
     * 实际数据库状态（用于不匹配场景）
     */
    public static final String ACTUAL_DB = "actual_db";

    /**
     * 事件中的上一个状态
     */
    public static final String EVENT_PREVIOUS = "event_previous";

    /**
     * 恢复标记
     * 标识该记录是否为恢复记录
     */
    public static final String RECOVERY = "recovery";

    /**
     * 从哪个状态恢复
     * 标识恢复前的状态
     */
    public static final String RECOVERED_FROM = "recovered_from";

    /**
     * 时间戳异常标记
     * 标识是否存在时间戳异常
     */
    public static final String TIMESTAMP_ANOMALY = "timestamp_anomaly";

    /**
     * 事件时间戳字段
     * 事件中的时间戳值
     */
    public static final String EVENT_TIMESTAMP = "event_timestamp";

    /**
     * 数据库时间戳字段
     * 数据库中使用的时间戳值
     */
    public static final String DB_TIMESTAMP = "db_timestamp";

    /**
     * 数据库开始时间戳字段
     * 数据库中状态记录的开始时间戳
     */
    public static final String DB_START_TS = "db_start_ts";

    /**
     * 异常原因字段
     * 记录时间戳异常或其他异常的原因
     */
    public static final String ANOMALY_REASON = "anomaly_reason";

    // ==================== 错误类型 ====================
    /**
     * 错误类型：状态不匹配
     */
    public static final String ERROR_TYPE_STATE_MISMATCH = "STATE_MISMATCH";

    /**
     * 错误类型：时间戳异常
     */
    public static final String ERROR_TYPE_TIMESTAMP_ANOMALY = "TIMESTAMP_ANOMALY";

    /**
     * 错误类型：未知状态值
     */
    public static final String ERROR_TYPE_UNKNOWN_STATE = "UNKNOWN_STATE";

    // ==================== 时间戳转换 ====================
    /**
     * 时间戳转换：毫秒转秒的除数
     * 用于将毫秒时间戳转换为秒时间戳
     */
    public static final long MILLIS_TO_SECONDS = 1000L;
}




