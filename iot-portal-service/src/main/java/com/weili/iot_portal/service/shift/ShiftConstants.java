package com.weili.iot_portal.service.shift;

/**
 * 班次相关常量
 */
public class ShiftConstants {

    private ShiftConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 时间格式 ====================
    /**
     * 时间格式：HH:mm:ss
     */
    public static final String TIME_FORMAT = "HH:mm:ss";

    // ==================== 班次编码 ====================
    /**
     * 班次1编码（数字编码）
     */
    public static final Integer SHIFT_CODE_1 = 1;

    /**
     * 班次2编码（数字编码）
     */
    public static final Integer SHIFT_CODE_2 = 2;

    /**
     * 班次3编码（数字编码）
     */
    public static final Integer SHIFT_CODE_3 = 3;

    // ==================== 默认班次名称 ====================
    /**
     * 2班制：早班名称
     */
    public static final String SHIFT_NAME_2MODE_DAY = "早班";

    /**
     * 2班制：晚班名称
     */
    public static final String SHIFT_NAME_2MODE_NIGHT = "晚班";

    /**
     * 3班制：第一班名称
     */
    public static final String SHIFT_NAME_3MODE_FIRST = "第一班";

    /**
     * 3班制：第二班名称
     */
    public static final String SHIFT_NAME_3MODE_SECOND = "第二班";

    /**
     * 3班制：第三班名称
     */
    public static final String SHIFT_NAME_3MODE_THIRD = "第三班";

    // ==================== 默认班次时间 ====================
    /**
     * 2班制早班开始时间
     */
    public static final String SHIFT_TIME_2MODE_DAY_START = "08:00:00";

    /**
     * 2班制早班结束时间（也是晚班开始时间）
     */
    public static final String SHIFT_TIME_2MODE_DAY_END = "20:00:00";

    /**
     * 2班制晚班结束时间
     */
    public static final String SHIFT_TIME_2MODE_NIGHT_END = "08:00:00";

    /**
     * 3班制第一班开始时间
     */
    public static final String SHIFT_TIME_3MODE_FIRST_START = "08:00:00";

    /**
     * 3班制第一班结束时间（也是第二班开始时间）
     */
    public static final String SHIFT_TIME_3MODE_FIRST_END = "16:00:00";

    /**
     * 3班制第二班结束时间（也是第三班开始时间）
     */
    public static final String SHIFT_TIME_3MODE_SECOND_END = "00:00:00";

    /**
     * 3班制第三班结束时间
     */
    public static final String SHIFT_TIME_3MODE_THIRD_END = "08:00:00";

    /**
     * 午夜时间（00:00:00）
     */
    public static final String TIME_MIDNIGHT = "00:00:00";

    // ==================== 班次时长（小时） ====================
    /**
     * 2班制每班时长（小时）
     */
    public static final int SHIFT_DURATION_HOURS_2MODE = 12;

    /**
     * 3班制每班时长（小时）
     */
    public static final int SHIFT_DURATION_HOURS_3MODE = 8;

    // ==================== 时间单位转换 ====================
    /**
     * 每小时秒数
     */
    public static final int SECONDS_PER_HOUR = 3600;

    // ==================== 班次模式 ====================
    /**
     * 2班制模式
     */
    public static final int SHIFT_MODE_2 = 2;

    /**
     * 3班制模式
     */
    public static final int SHIFT_MODE_3 = 3;

    // ==================== 错误消息 ====================
    /**
     * 不支持的班次模式错误消息前缀
     */
    public static final String ERROR_UNSUPPORTED_SHIFT_MODE_PREFIX = "不支持的班次模式: ";

    /**
     * 不支持的班次模式错误消息后缀
     */
    public static final String ERROR_UNSUPPORTED_SHIFT_MODE_SUFFIX = "，仅支持2（2班制）或3（3班制）";
}

