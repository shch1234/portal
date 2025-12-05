package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;

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
     */
    void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception;

    /**
     * 排序：数值越小优先级越高
     */
    default int order() {
        return 0;
    }
}

