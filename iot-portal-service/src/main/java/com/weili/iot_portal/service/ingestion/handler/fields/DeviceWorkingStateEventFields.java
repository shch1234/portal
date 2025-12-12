package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备加工状态事件字段常量定义
 * <p>
 * 用于 DEVICE_WORKING_STATE 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 * <p>
 * 加工状态：开始为1，结束为0
 * </p>
 *
 * @author system
 */
public final class DeviceWorkingStateEventFields {

    private DeviceWorkingStateEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型：设备加工状态事件
     */
    public static final String EVENT_TYPE = "DEVICE_WORKING_STATE";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceWorkingStateEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceWorkingStateEvent";

    // ==================== 事件数据字段 ====================
    /**
     * 上一个加工状态字段
     * 表示状态变更前的状态，0表示结束，1表示开始
     */
    public static final String PREVIOUS_STATUS = "previousStatus";

    /**
     * 当前加工状态字段（必填）
     * 表示状态变更后的当前状态，0表示结束，1表示开始
     */
    public static final String CURRENT_STATUS = "currentStatus";

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

    // ==================== 加工状态值 ====================
    /**
     * 加工状态：结束
     */
    public static final int STATUS_END = 0;

    /**
     * 加工状态：开始
     */
    public static final int STATUS_START = 1;

    // ==================== 分布式锁相关 ====================

    /**
     * 分布式锁超时时间（秒）
     * 加工状态操作的锁超时时间，防止死锁
     */
    public static final long LOCK_TIMEOUT_SECONDS = 5L;

    /**
     * 分布式锁值
     * Redis 锁的占位值，表示锁已被占用
     */
    public static final String LOCK_VALUE = "1";

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
}

