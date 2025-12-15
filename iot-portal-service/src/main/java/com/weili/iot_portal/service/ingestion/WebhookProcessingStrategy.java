package com.weili.iot_portal.service.ingestion;

/**
 * Webhook 处理策略枚举
 * <p>
 * 用于定义不同事件类型的处理方式，根据事件特性选择合适的处理策略
 * </p>
 *
 * @author system
 */
public enum WebhookProcessingStrategy {

    /**
     * 实时直接处理：不经过收件箱，直接调用Handler
     * <p>
     * 适用场景：
     * - 只写Redis缓存的事件（DEVICE_PROGRAM, DEVICE_AXIS等）
     * - 对实时性要求高，允许丢失
     * - 不需要持久化保证
     * </p>
     * <p>
     * 处理方式：
     * - 直接调用Handler.handle(null, request)
     * - 失败只记录日志，不重试
     * - 不保存到收件箱
     * </p>
     */
    REALTIME_DIRECT,

    /**
     * 业务持久化处理：经过收件箱，支持重试
     * <p>
     * 适用场景：
     * - 需要写数据库的事件（DEVICE_STATE, DEVICE_TOOL_CHANGE等）
     * - 需要保证数据不丢失
     * - 支持失败重试
     * </p>
     * <p>
     * 处理方式：
     * - 保存到收件箱
     * - 异步处理，支持重试
     * - 失败记录到失败日志表
     * </p>
     */
    BUSINESS_PERSISTENT,

    /**
     * 实时但需持久化：REALTIME类别但需要写数据库
     * <p>
     * 适用场景：
     * - REALTIME类别但需要写数据库（如DEVICE_TOOL需要写device_tool_compensation表）
     * - 需要实时性，但也需要数据持久化
     * </p>
     * <p>
     * 处理方式：
     * - 直接调用Handler.handle(null, request)
     * - Handler内部有@Transactional保证数据一致性
     * - 失败只记录日志，不重试（保持REALTIME的低可靠性特性）
     * - 不保存到收件箱（减少延迟）
     * </p>
     */
    REALTIME_WITH_PERSISTENCE
}

