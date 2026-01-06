package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备轴事件字段常量定义
 * <p>
 * 用于 DEVICE_AXIS 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceAxisEventFields {

    private DeviceAxisEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "DEVICE_AXIS";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceAxisEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceAxisEvent";

    // ==================== 字段前缀 ====================
    /**
     * 轴字段前缀：axis.
     * 例如：axis.X.absolute, axis.X.relative, axis.Y.absolute 等
     * 表示各个轴的绝对坐标、相对坐标等信息
     */
    public static final String AXIS_PREFIX = "axis.";

    // ==================== 系统元数据字段 ====================
    /**
     * 更新时间字段
     * 记录数据最后更新的时间戳（毫秒）
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

    // ==================== 倍率相关字段 ====================
    /**
     * 倍率值字段：ratio
     * 表示加工倍率
     */
    public static final String RATIO = "ratio";

    /**
     * 倍率值字段：override（别名）
     * 表示倍率覆盖值，与 ratio 含义相同
     */
    public static final String OVERRIDE = "override";

    // ==================== 曲线指标字段 ====================
    /**
     * 负载指标
     * 用于负载曲线数据
     */
    public static final String METRIC_LOAD = "load";

    /**
     * 转速指标
     * 用于转速曲线数据
     */
    public static final String METRIC_RPM = "rpm";

    /**
     * 进给指标
     * 用于进给曲线数据
     */
    public static final String METRIC_FEED = "feed";

    // ==================== 默认值 ====================
    /**
     * 默认空值占位符
     * 当工厂ID或设备ID为空时，使用此值作为 Redis Key 的占位符
     */
    public static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 辅助方法 ====================
    /**
     * 判断字段名是否为轴相关字段
     *
     * @param fieldName 字段名
     * @return true 如果以 axis. 开头
     */
    public static boolean isAxisField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return fieldName.trim().startsWith(AXIS_PREFIX);
    }
}




