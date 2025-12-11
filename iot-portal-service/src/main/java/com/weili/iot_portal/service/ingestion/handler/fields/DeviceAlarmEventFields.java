package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备报警事件字段常量定义
 * <p>
 * 用于 DEVICE_ALARM 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceAlarmEventFields {

    private DeviceAlarmEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "DEVICE_ALARM";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceAlarmEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceAlarmEvent";

    // ==================== 事件数据字段 ====================
    /**
     * 报警数组字段
     * 包含多个报警对象的数组，每个对象包含 alarmCode、alarmText、alarmLevel
     */
    public static final String ALARMS = "alarms";

    // ==================== 报警对象字段 ====================
    /**
     * 报警代码字段（必填）
     * 唯一标识一个报警的代码
     */
    public static final String ALARM_CODE = "alarmCode";

    /**
     * 报警文本字段（可选）
     * 报警的详细描述信息
     */
    public static final String ALARM_TEXT = "alarmText";

    /**
     * 报警级别字段（可选）
     * 报警的严重程度级别
     */
    public static final String ALARM_LEVEL = "alarmLevel";

    // ==================== 活跃状态常量 ====================
    /**
     * 活跃状态：激活
     * 表示报警处于活跃状态（未解除）
     */
    public static final int ACTIVE_STATUS_ENABLED = 1;

    /**
     * 活跃状态：禁用
     * 表示报警已解除（已关闭）
     */
    public static final int ACTIVE_STATUS_DISABLED = 0;

    // ==================== 时间戳转换 ====================
    /**
     * 时间戳转换：毫秒转秒的除数
     * 用于将毫秒时间戳转换为秒时间戳
     */
    public static final long MILLIS_TO_SECONDS = 1000L;
}

