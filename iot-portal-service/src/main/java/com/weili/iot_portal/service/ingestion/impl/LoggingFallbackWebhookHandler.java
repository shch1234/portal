package com.weili.iot_portal.service.ingestion.impl;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.WebhookHandlerOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认兜底 Handler：不真正处理，仅抛出异常提示未匹配
 */
@Slf4j
@Order(WebhookHandlerOrder.FALLBACK) // 最低优先级
@Component
public class LoggingFallbackWebhookHandler implements WebhookEventHandler {
    @Override
    public boolean supports(String eventType) {
        // 兜底匹配所有
        return true;
    }

    @Override
    public void handle(WebhookInboxDO inbox, WebhookRequest request) {
        log.warn("未找到匹配的 Webhook Handler, eventType={}, messageId={}",
                request.getEventType(), request.getMessageId());
        throw new IotPortalException(IotPortalErrorCode.WEBHOOK_EVENT_TYPE_UNSUPPORTED, "Unsupported eventType: " + request.getEventType());
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.FALLBACK;
    }
}

