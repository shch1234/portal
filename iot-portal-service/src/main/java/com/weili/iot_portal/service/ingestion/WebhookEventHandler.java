package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;

import java.util.Map;

/**
 * Webhook 事件处理接口，支持按 eventType 动态分发
 */
public interface WebhookEventHandler {

    /**
     * 是否支持当前事件类型
     */
    boolean supports(String eventType);

    /**
     * 处理事件
     * <p>
     * 注意：对于 REALTIME_DIRECT 和 REALTIME_WITH_PERSISTENCE 策略，inbox 参数为 null
     * </p>
     * 
     * @param inbox 收件箱对象（BUSINESS_PERSISTENT策略时有效，其他策略为null）
     * @param request Webhook请求对象
     * @throws Exception 处理异常
     */
    void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception;

    /**
     * 实时处理事件（可选重载方法）
     * <p>
     * 对于 REALTIME_DIRECT 和 REALTIME_WITH_PERSISTENCE 策略的Handler，
     * 可以实现此方法以提供更清晰的接口（不需要inbox参数）
     * </p>
     * <p>
     * 默认实现：调用 handle(null, request)，保持向后兼容
     * </p>
     * 
     * @param request Webhook请求对象
     * @throws Exception 处理异常
     */
    default void handleRealtime(WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }
        // 默认实现：调用原有方法，inbox为null
        handle(null, request);
    }

    /**
     * 排序：数值越小优先级越高
     */
    default int order() {
        return 0;
    }

    /**
     * 获取处理策略
     * <p>
     * 根据事件特性返回合适的处理策略：
     * - REALTIME_DIRECT: 实时直接处理，只写Redis，不持久化
     * - BUSINESS_PERSISTENT: 业务持久化处理，经过收件箱，支持重试
     * - REALTIME_WITH_PERSISTENCE: 实时但需持久化，直接处理但Handler内部有事务
     * </p>
     * 
     * @return 处理策略，默认为 BUSINESS_PERSISTENT（保证向后兼容）
     */
    default WebhookProcessingStrategy getProcessingStrategy() {
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }
}

