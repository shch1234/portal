package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备刀具事件字段常量定义
 * <p>
 * 用于 DEVICE_TOOL 事件处理，定义事件数据中可能出现的字段名称及其含义
 * </p>
 *
 * @author system
 */
public final class DeviceToolEventFields {

    private DeviceToolEventFields() {
        // 工具类，禁止实例化
    }

    /**
     * 事件类型：刀具事件
     */
    public static final String EVENT_TYPE = "DEVICE_TOOL";

    /**
     * 事件类型：刀具变更事件
     */
    public static final String EVENT_TYPE_CHANGE = "DEVICE_TOOL_CHANGE";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceToolEvent 处理器
     */
    public static final String EVENT_SOURCE = "DeviceToolEvent";

    /**
     * 事件来源标识（用于日志和追踪）
     * 标识该事件来自 DeviceToolChangeEvent 处理器
     */
    public static final String EVENT_SOURCE_CHANGE = "DeviceToolChangeEvent";

    // ==================== 刀具编号相关字段 ====================
    /**
     * 刀具编号（标准字段名）
     * 用于标识当前使用的刀具编号
     */
    public static final String TOOL_NUMBER = "toolNumber";

    /**
     * 刀具编号（别名1：驼峰命名）
     * 兼容不同的命名风格
     */
    public static final String TOOL_NO = "toolNo";

    /**
     * 刀具编号（别名2：下划线命名）
     * 兼容不同的命名风格
     */
    public static final String TOOL_NUM = "tool_num";

    // ==================== 刀架/刀补号相关字段 ====================
    /**
     * 刀架号/刀补号（标准字段名）
     * 用于标识刀具所在的刀架位置编号，是刀补补偿的关键标识
     */
    public static final String HOLDER_NUMBER = "holderNumber";

    /**
     * 刀架号（别名1：驼峰命名）
     * 兼容不同的命名风格
     */
    public static final String TOOL_HOLDER = "toolHolder";

    /**
     * 刀架号（别名2：下划线命名）
     * 兼容不同的命名风格
     */
    public static final String HOLDER_NUM = "holder_num";

    // ==================== 刀补值相关字段前缀 ====================
    /**
     * 刀补值字段前缀：offset
     * 例如：offsetX, offsetY, offsetZ, offsetR 等
     * 表示刀具在各个轴向上的补偿值
     */
    public static final String OFFSET_PREFIX = "offset";

    /**
     * 补偿值字段前缀：comp
     * 例如：compX, compY, compZ 等
     * 表示补偿值（与 offset 类似，但可能来自不同的数据源）
     */
    public static final String COMP_PREFIX = "comp";

    /**
     * 刀具相关字段前缀：tool
     * 例如：toolType, toolLength, toolDiameter 等
     * 表示刀具相关的属性信息
     */
    public static final String TOOL_PREFIX = "tool";

    /**
     * 刀架相关字段前缀：holder
     * 例如：holderType, holderLength 等
     * 表示刀架相关的属性信息
     */
    public static final String HOLDER_PREFIX = "holder";

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

    // ==================== 事件数据中的字段名（用于提取补偿值）====================
    /**
     * 事件数据中 previousToolNo 字段
     * 表示上一次使用的刀具编号（用于刀具变更事件）
     */
    public static final String PREVIOUS_TOOL_NO = "previousToolNo";

    /**
     * 事件数据中 currentToolNo 字段
     * 表示当前使用的刀具编号（用于刀具变更事件）
     */
    public static final String CURRENT_TOOL_NO = "currentToolNo";

    /**
     * 事件数据中 toolHolderNumber 字段
     * 表示刀架编号（用于刀具变更事件）
     */
    public static final String TOOL_HOLDER_NUMBER = "toolHolderNumber";

    /**
     * 事件数据中 toolMagazineNo 字段
     * 表示刀库编号
     */
    public static final String TOOL_MAGAZINE_NO = "toolMagazineNo";

    /**
     * 事件数据中 toolId 字段
     * 表示刀具ID
     */
    public static final String TOOL_ID = "toolId";

    /**
     * 事件数据中 toolType 字段
     * 表示刀具类型
     */
    public static final String TOOL_TYPE = "toolType";

    // ==================== 业务常量值 ====================
    /**
     * 初始版本号
     * 新创建的刀补补偿记录的初始版本号
     */
    public static final int INITIAL_VERSION = 1;

    /**
     * 活跃状态：激活
     * 表示刀补补偿记录处于活跃状态
     */
    public static final int ACTIVE_STATUS_ENABLED = 1;

    /**
     * 活跃状态：禁用
     * 表示刀补补偿记录已关闭（被新版本替代）
     */
    public static final int ACTIVE_STATUS_DISABLED = 0;

    /**
     * 时间戳转换：毫秒转秒的除数
     * 用于将毫秒时间戳转换为秒时间戳
     */
    public static final long MILLIS_TO_SECONDS = 1000L;

    // ==================== 刀具变更事件相关 ====================

    /**
     * 分布式锁超时时间（秒）：刀具变更
     * 刀具变更操作的锁超时时间，防止死锁
     */
    public static final long LOCK_TIMEOUT_SECONDS_TOOL_CHANGE = 5L;

    // ==================== 辅助方法 ====================
    /**
     * 判断字段名是否为刀具编号的别名
     *
     * @param fieldName 字段名
     * @return true 如果是刀具编号相关字段
     */
    public static boolean isToolNumberField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return TOOL_NUMBER.equalsIgnoreCase(key)
                || TOOL_NO.equalsIgnoreCase(key)
                || TOOL_NUM.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否为刀架号的别名
     *
     * @param fieldName 字段名
     * @return true 如果是刀架号相关字段
     */
    public static boolean isHolderNumberField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return HOLDER_NUMBER.equalsIgnoreCase(key)
                || TOOL_HOLDER.equalsIgnoreCase(key)
                || HOLDER_NUM.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否以刀具相关前缀开头
     *
     * @param fieldName 字段名
     * @return true 如果以 tool/holder/offset 开头
     */
    public static boolean isToolRelatedField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim().toLowerCase();
        return key.startsWith(TOOL_PREFIX.toLowerCase())
                || key.startsWith(HOLDER_PREFIX.toLowerCase())
                || key.startsWith(OFFSET_PREFIX.toLowerCase());
    }

    /**
     * 判断字段名是否用于补偿值提取
     *
     * @param fieldName 字段名
     * @return true 如果以 offset/comp/tool/holder 开头
     */
    public static boolean isCompensationField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim().toLowerCase();
        return key.startsWith(OFFSET_PREFIX.toLowerCase())
                || key.startsWith(COMP_PREFIX.toLowerCase())
                || key.startsWith(TOOL_PREFIX.toLowerCase())
                || key.startsWith(HOLDER_PREFIX.toLowerCase());
    }

}

