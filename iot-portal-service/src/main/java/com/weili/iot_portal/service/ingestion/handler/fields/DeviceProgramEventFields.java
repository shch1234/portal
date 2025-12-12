package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备程序事件字段常量定义
 * <p>
 * 用于 DEVICE_PROGRAM 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceProgramEventFields {

    private DeviceProgramEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型
     */
    public static final String EVENT_TYPE = "DEVICE_PROGRAM";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceProgramEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceProgramEvent";

    // ==================== 程序相关字段 ====================
    /**
     * 程序名称（标准字段名）
     * 表示当前执行的程序名称
     */
    public static final String PROGRAM_NAME = "programName";

    /**
     * 程序名称（别名1）
     * 兼容不同的命名风格
     */
    public static final String PROGRAM = "program";

    /**
     * 程序路径（标准字段名）
     * 表示程序的存储路径
     */
    public static final String PROGRAM_PATH = "programPath";

    /**
     * 程序路径（别名：下划线命名）
     * 兼容不同的命名风格
     */
    public static final String PROGRAM_PATH_UNDERSCORE = "program_path";

    // ==================== G代码/M代码相关字段 ====================
    /**
     * G代码（标准字段名）
     * 表示当前执行的G代码
     */
    public static final String G_CODE = "gCode";

    /**
     * G代码（别名：全小写）
     * 兼容不同的命名风格
     */
    public static final String G_CODE_LOWERCASE = "gcode";

    /**
     * M代码（标准字段名）
     * 表示当前执行的M代码
     */
    public static final String M_CODE = "mCode";

    /**
     * M代码（别名：全小写）
     * 兼容不同的命名风格
     */
    public static final String M_CODE_LOWERCASE = "mcode";

    // ==================== 字段前缀 ====================
    /**
     * 程序相关字段前缀：program
     * 例如：programName, programPath, programVersion 等
     */
    public static final String PROGRAM_PREFIX = "program";

    /**
     * G代码相关字段前缀：gCode
     * 例如：gCode, gCode1, gCode2 等
     */
    public static final String G_CODE_PREFIX = "gCode";

    /**
     * M代码相关字段前缀：mCode
     * 例如：mCode, mCode1, mCode2 等
     */
    public static final String M_CODE_PREFIX = "mCode";

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

    // ==================== 默认值 ====================
    /**
     * 默认空值占位符
     * 当工厂ID或设备ID为空时，使用此值作为 Redis Key 的占位符
     */
    public static final String DEFAULT_BLANK_PLACEHOLDER = "none";

    // ==================== 辅助方法 ====================
    /**
     * 判断字段名是否为程序名称的别名
     *
     * @param fieldName 字段名
     * @return true 如果是程序名称相关字段
     */
    public static boolean isProgramNameField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return PROGRAM_NAME.equalsIgnoreCase(key) || PROGRAM.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否为程序路径的别名
     *
     * @param fieldName 字段名
     * @return true 如果是程序路径相关字段
     */
    public static boolean isProgramPathField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return PROGRAM_PATH.equalsIgnoreCase(key) || PROGRAM_PATH_UNDERSCORE.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否为G代码的别名
     *
     * @param fieldName 字段名
     * @return true 如果是G代码相关字段
     */
    public static boolean isGCodeField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return G_CODE.equalsIgnoreCase(key) || G_CODE_LOWERCASE.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否为M代码的别名
     *
     * @param fieldName 字段名
     * @return true 如果是M代码相关字段
     */
    public static boolean isMCodeField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return M_CODE.equalsIgnoreCase(key) || M_CODE_LOWERCASE.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否以程序相关前缀开头
     *
     * @param fieldName 字段名
     * @return true 如果以 program/gCode/mCode 开头
     */
    public static boolean isProgramRelatedField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim().toLowerCase();
        return key.startsWith(PROGRAM_PREFIX.toLowerCase())
                || key.startsWith(G_CODE_PREFIX.toLowerCase())
                || key.startsWith(M_CODE_PREFIX.toLowerCase());
    }
}




