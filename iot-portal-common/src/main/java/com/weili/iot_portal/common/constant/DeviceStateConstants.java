package com.weili.iot_portal.common.constant;

/**
 * 设备状态相关常量
 * <p>
 * 定义设备状态相关的常量，包括数据库 properties 字段的 key
 * </p>
 */
public final class DeviceStateConstants {

    private DeviceStateConstants() {
        // 工具类，禁止实例化
    }

    // ==================== Properties 字段常量 ====================
    /**
     * 原始状态字段
     * 用于记录状态转换前的原始状态值
     */
    public static final String PROP_ORIGINAL_STATE = "original_state";

    /**
     * 状态转换标记
     * 标识该状态是否经过转换
     */
    public static final String PROP_STATE_CONVERTED = "state_converted";

    /**
     * 状态转换原因
     * 记录状态转换的原因
     */
    public static final String PROP_CONVERSION_REASON = "conversion_reason";

    /**
     * 状态转换时间戳
     * 记录状态转换的时间戳
     */
    public static final String PROP_CONVERSION_TIMESTAMP = "conversion_timestamp";
}

