package com.weili.iot_portal.service.ingestion.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.WebhookHandlerOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认兜底 Handler：不真正处理，仅抛出异常提示未匹配
 * <p>
 * 注意：此 Handler 的 supports 方法返回 true，但 order 是最低优先级（9999），
 * 所以只有在没有其他 Handler 匹配时才会被调用。
 * </p>
 */
@Slf4j
@Order(WebhookHandlerOrder.FALLBACK) // 最低优先级
@Component("loggingFallbackWebhookHandler")
public class LoggingFallbackWebhookHandler implements WebhookEventHandler {
    @Override
    public boolean supports(String eventType) {
        // 兜底匹配所有（但优先级最低，只有在没有其他 Handler 匹配时才会被调用）
        // 注意：如果其他 Handler 的 supports 方法正确实现，此 Handler 不应该被调用
        return true;
    }

    @Override
    public void handle(WebhookInboxDO inbox, WebhookRequest request) {
        log.error("未找到匹配的 Webhook Handler（这不应该发生，请检查 Handler 注册）: eventType={}, messageId={}, deviceCode={}",
                request.getEventType(), request.getMessageId(), request.getDeviceCode());
        throw new IotPortalException(IotPortalErrorCode.WEBHOOK_EVENT_TYPE_UNSUPPORTED, 
                "Unsupported eventType: " + request.getEventType() + 
                " (如果此事件类型应该被支持，请检查对应的 Handler 是否正确注册和实现 supports 方法)");
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.FALLBACK;
    }
    
    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 兜底 Handler 使用业务持久化策略，确保错误信息被记录
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }
}

