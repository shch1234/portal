package com.weili.iot_portal.service.webhook;

import com.weili.basic.common.util.JsonUtils;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookMonitorService;
import com.weili.basic.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Webhook 收件箱异步处理 Worker（占位实现）
 * 后续可按 eventType 分发到具体业务 Handler
 */
@Slf4j
@Service
public class WebhookProcessWorker {

    @Autowired
    private WebhookInboxService inboxService;

    @Autowired
    private WebhookHandlerRegistry handlerRegistry;

    @Autowired
    private WebhookMonitorService monitorService;

    @Autowired
    private WebhookFailLogService webhookFailLogService;

    /**
     * 批量处理待办
     * @return 处理成功条数
     */
    public int processBatch() {
        List<WebhookInboxDO> inboxList = inboxService.fetchDue();
        if (inboxList.isEmpty()) {
            return 0;
        }

        int success = 0;
        for (WebhookInboxDO inbox : inboxList) {
            if (processSingleInbox(inbox)) {
                success++;
            }
        }
        return success;
    }

    /**
     * 处理单个收件箱消息
     * @param inbox 待处理的收件箱消息
     * @return 是否处理成功
     */
    private boolean processSingleInbox(WebhookInboxDO inbox) {
        long start = System.currentTimeMillis();
        WebhookRequest request = null;
        try {
            inboxService.markProcessing(inbox);
            request = JsonUtils.convertObject(inbox.getPayload(), WebhookRequest.class);
            populateRequestFields(inbox, request);

            Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(inbox.getEventType());
            if (handlerOpt.isEmpty()) {
                handleNoHandlerCase(inbox, request);
                return false;
            }

            WebhookEventHandler handler = handlerOpt.get();
            monitorService.recordMatched(inbox.getEventType(), handler.getClass().getSimpleName());

            handler.handle(inbox, request);
            inboxService.markSuccess(inbox);
            monitorService.recordSuccess(inbox.getEventType(), System.currentTimeMillis() - start);
            return true;

        } catch (Exception ex) {
            handleProcessingException(inbox, request, ex, start);
            return false;
        }
    }

    /**
     * 填充 WebhookRequest 基础字段
     */
    private void populateRequestFields(WebhookInboxDO inbox, WebhookRequest request) {
        request.setMessageId(inbox.getMessageId());
        request.setDeviceCode(inbox.getDeviceCode());
        request.setDeviceId(inbox.getTbDeviceId());
        request.setTenantId(inbox.getTenantUuid());
        request.setEventType(inbox.getEventType());
        request.setWebhookCategory(inbox.getWebhookCategory());
    }

    /**
     * 处理无对应处理器的情况
     */
    private void handleNoHandlerCase(WebhookInboxDO inbox, WebhookRequest request) {
        String errorMessage = "Unsupported eventType: " + inbox.getEventType();
        inboxService.markFailedNoRetry(inbox, errorMessage);
        webhookFailLogService.saveFailLog(request, "VALIDATION", errorMessage, true);
        monitorService.recordUnmatched(inbox.getEventType());
    }

    /**
     * 处理消息处理过程中的异常
     */
    private void handleProcessingException(WebhookInboxDO inbox, WebhookRequest request, Exception ex, long start) {
        long cost = System.currentTimeMillis() - start;
        log.error("Webhook 收件箱处理失败: messageId={}, eventType={}",
                inbox.getMessageId(), inbox.getEventType(), ex);

        inboxService.markFailed(inbox, ex.getMessage());

        if (inboxService.reachMaxRetry(inbox)) {
            if (request == null) {
                request = JsonUtils.convertObject(inbox.getPayload(), WebhookRequest.class);
                populateRequestFields(inbox, request);
            }

            boolean needManual = ex instanceof ServiceException;
            webhookFailLogService.saveFailLog(request, "PROCESS", ex.getMessage(), needManual);
        }

        monitorService.recordFailure(inbox.getEventType(), ex.getMessage(), cost, true);
    }
}
