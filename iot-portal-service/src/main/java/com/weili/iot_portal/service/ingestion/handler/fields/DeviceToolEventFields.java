package com.weili.iot_portal.service.ingestion.handler.fields;

/**
 * 设备刀具事件字段常量定义
 * <p>
 * 用于 DEVICE_TOOL 和 DEVICE_TOOL_CHANGE 事件处理，定义事件数据中可能出现的字段名称及其含义。
 * </p>
 * <p>
 * 文件结构说明：
 * 1. 事件类型常量
 * 2. 核心字段（刀具编号、刀补号）
 * 3. 补偿数据相关（字段前缀、结构化字段）
 * 4. 事件数据字段（用于刀具变更事件）
 * 5. 系统元数据字段
 * 6. 业务常量（版本号、状态值、锁超时等）
 * 7. 辅助方法（字段判断）
 * </p>
 *
 * @author system
 */
public final class DeviceToolEventFields {

    private DeviceToolEventFields() {
        // 工具类，禁止实例化
    }

    // ==================== 事件类型 ====================
    /**
     * 事件类型：刀具事件
     * 用于实时更新刀具补偿数据
     */
    public static final String EVENT_TYPE = "DEVICE_TOOL";

    /**
     * 事件类型：刀具变更事件
     * 用于记录刀具使用历史
     */
    public static final String EVENT_TYPE_CHANGE = "DEVICE_TOOL_CHANGE";

    // ==================== 核心字段：刀具编号和刀补号 ====================
    /**
     * 刀具编号（标准字段名）
     * 用于标识当前使用的刀具编号
     * 允许值为0，0表示"未使用刀具"
     */
    public static final String TOOL_NO = "toolNo";

    /**
     * 刀补号（标准字段名）
     * 用于标识刀具所在的刀架位置编号，是刀补补偿的关键标识
     * 值为0时表示未使用刀补
     */
    public static final String HOLDER_NUMBER = "holderNumber";

    /**
     * 刀补号别名：hNo
     * 发那科/科德：长度补偿号
     * 西门子：复用为刀沿号（冗余保留）
     * 优先级：最高（用于提取刀补号）
     */
    public static final String H_NO = "hNo";

    /**
     * 刀补号别名：toolEdgeNumber
     * 适配西门子：刀沿号（D号）
     * 优先级：中等（用于提取刀补号）
     */
    public static final String TOOL_EDGE_NUMBER = "toolEdgeNumber";

    /**
     * 刀补号别名：dNo
     * 发那科/科德：半径补偿号
     * 西门子：复用为刀沿号（冗余保留）
     * 优先级：最低（用于提取刀补号）
     */
    public static final String D_NO = "dNo";

    // ==================== 补偿数据：字段前缀（用于扁平化数据提取）====================
    /**
     * 补偿值字段前缀：offset
     * 用于识别扁平化格式的补偿字段
     * 例如：offsetX, offsetY, offsetZ, offsetR 等
     * 表示几何补偿值
     */
    public static final String OFFSET_PREFIX = "offset";

    /**
     * 补偿值字段前缀：comp
     * 用于识别扁平化格式的补偿字段
     * 例如：compX, compY, compZ 等
     * 表示磨损补偿值
     */
    public static final String COMP_PREFIX = "comp";

    // ==================== 补偿数据：结构化字段（用于结构化数据提取）====================
    /**
     * 结构化补偿字段名
     * 设备发送的补偿对象字段名
     * 例如：{"compensation": {"geom": {"offsetX": 0.5}, "wear": {"compX": 0.1}}}
     */
    public static final String COMPENSATION_FIELD = "compensation";

    // ==================== 事件数据字段（用于刀具变更事件）====================
    /**
     * 上一个刀具编号
     * 用于刀具变更事件，表示上一次使用的刀具编号
     */
    public static final String PREVIOUS_TOOL_NO = "previousToolNo";

    /**
     * 当前刀具编号
     * 用于刀具变更事件，表示当前使用的刀具编号
     */
    public static final String CURRENT_TOOL_NO = "currentToolNo";

    /**
     * 刀套号（刀库编号）
     * 用于刀具变更事件，表示刀具在刀库中的物理位置（刀套号）
     * <p>
     * 注意：
     * 1. 如果 eventData 中没有提供 toolMagazineNo，则使用 toolNo 作为备选
     *    （因为 toolNo 通常也表示刀具在刀库中的位置号）
     * 2. toolMagazineNo 和 holderNumber（刀补号）是不同的概念：
     *    - toolMagazineNo：刀具在刀库中的物理位置
     *    - holderNumber：刀具在刀架上的位置，用于补偿计算
     * </p>
     */
    public static final String TOOL_MAGAZINE_NO = "toolMagazineNo";

    /**
     * 刀具ID
     * 用于刀具变更事件，表示刀具ID
     */
    public static final String TOOL_ID = "toolId";

    /**
     * 刀具类型
     * 用于刀具变更事件，表示刀具类型
     */
    public static final String TOOL_TYPE = "toolType";

    // ==================== 系统元数据字段 ====================
    /**
     * 更新时间字段
     * 记录数据最后更新的时间戳（毫秒）
     * 用于Redis缓存
     */
    public static final String UPDATED_AT = "updatedAt";

    /**
     * 数据来源字段
     * 标识数据来源，通常为 "TB"（ThingsBoard）
     * 用于Redis缓存
     */
    public static final String SOURCE = "source";

    /**
     * 数据来源值：ThingsBoard
     */
    public static final String SOURCE_TB = "TB";

    /**
     * 追踪ID字段
     * 用于链路追踪，通常使用 messageId
     * 用于Redis缓存
     */
    public static final String TRACE_ID = "traceId";

    // ==================== 业务常量 ====================
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

    /**
     * 分布式锁超时时间（秒）：刀具变更
     * 刀具变更操作的锁超时时间，防止死锁
     */
    public static final long LOCK_TIMEOUT_SECONDS_TOOL_CHANGE = 5L;

    /**
     * 错误类型：刀具不匹配
     * 用于记录刀具变更事件中的异常情况
     */
    public static final String ERROR_TYPE_TOOL_MISMATCH = "TOOL_MISMATCH";

    // ==================== 辅助方法：字段判断 ====================
    /**
     * 判断字段名是否为刀具编号字段
     * <p>
     * 目前只支持标准字段名 toolNo
     * </p>
     *
     * @param fieldName 字段名
     * @return true 如果是刀具编号字段
     */
    public static boolean isToolNumberField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return TOOL_NO.equalsIgnoreCase(fieldName.trim());
    }

    /**
     * 判断字段名是否为刀补号相关字段
     * <p>
     * 支持的字段（按优先级）：
     * 1. hNo（优先级最高）
     * 2. toolEdgeNumber
     * 3. dNo
     * 4. holderNumber（标准字段名）
     * </p>
     *
     * @param fieldName 字段名
     * @return true 如果是刀补号相关字段
     */
    public static boolean isHolderNumberField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim();
        return HOLDER_NUMBER.equalsIgnoreCase(key)
                || H_NO.equalsIgnoreCase(key)
                || TOOL_EDGE_NUMBER.equalsIgnoreCase(key)
                || D_NO.equalsIgnoreCase(key);
    }

    /**
     * 判断字段名是否以补偿值前缀开头（用于提取刀具相关字段）
     * <p>
     * 用于提取所有以 offset 开头的字段（如 offsetX, offsetY 等）
     * 这些字段会被存入Redis缓存
     * </p>
     *
     * @param fieldName 字段名
     * @return true 如果以 offset 开头
     */
    public static boolean isToolRelatedField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim().toLowerCase();
        return key.startsWith(OFFSET_PREFIX.toLowerCase());
    }

    /**
     * 判断字段名是否用于补偿值提取（用于提取补偿快照）
     * <p>
     * 用于提取所有以 offset 或 comp 开头的字段（如 offsetX, compX 等）
     * 这些字段会被作为补偿快照存入数据库
     * </p>
     *
     * @param fieldName 字段名
     * @return true 如果以 offset/comp 开头
     */
    public static boolean isCompensationField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String key = fieldName.trim().toLowerCase();
        return key.startsWith(OFFSET_PREFIX.toLowerCase())
                || key.startsWith(COMP_PREFIX.toLowerCase());
    }
}
