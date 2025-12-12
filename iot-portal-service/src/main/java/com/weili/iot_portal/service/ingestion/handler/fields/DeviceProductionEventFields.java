package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备生产事件字段常量定义
 * <p>
 * 用于 DEVICE_PRODUCTION 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceProductionEventFields {

    private DeviceProductionEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "DEVICE_PRODUCTION";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceProductionEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceProductionEvent";

    // ==================== 事件数据字段 ====================
    /**
     * 状态字段（必填）
     * 表示生产状态：start（开始）或 end（结束）
     */
    public static final String STATUS = "status";
    /**
     * 程序名称字段（可选）
     * 表示当前执行的程序名称
     */
    public static final String PROGRAM_NAME = "programName";

    /**
     * 计数来源字段（可选）
     * 表示产量计数的来源
     */
    public static final String COUNT_SOURCE = "countSource";

    // ==================== 状态值常量 ====================
    /**
     * 状态值：开始
     * 表示生产开始
     */
    public static final String STATUS_START = "start";

    /**
     * 状态值：结束
     * 表示生产结束
     */
    public static final String STATUS_END = "end";

    // ==================== 时间戳转换 ====================
    /**
     * 时间戳转换：秒转毫秒的倍数
     * 用于将秒时间戳转换为毫秒时间戳
     */
    public static final long SECONDS_TO_MILLIS = 1000L;

    // ==================== 辅助方法 ====================
    /**
     * 判断状态值是否为有效的生产状态
     *
     * @param status 状态值
     * @return true 如果是 start 或 end
     */
    public static boolean isValidStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim();
        return STATUS_START.equalsIgnoreCase(s) || STATUS_END.equalsIgnoreCase(s);
    }
}



