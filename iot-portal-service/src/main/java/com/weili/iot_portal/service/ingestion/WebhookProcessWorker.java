package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookMonitorService;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.basic.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

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
            long start = System.currentTimeMillis();
            WebhookRequest request = null;
            try {
                inboxService.markProcessing(inbox);
                request = objectMapper.convertValue(inbox.getPayload(), WebhookRequest.class);
                // 填充基础字段
                request.setMessageId(inbox.getMessageId());
                request.setDeviceCode(inbox.getDeviceCode());
                request.setDeviceId(inbox.getTbDeviceId());
                request.setEventType(inbox.getEventType());
                request.setWebhookCategory(inbox.getWebhookCategory());

                Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(inbox.getEventType());
                if (handlerOpt.isEmpty()) {
                    // 未匹配到 Handler，直接标记失败（不进入重复重试）并告警
                    String errorMessage = "Unsupported eventType: " + inbox.getEventType();
                    inboxService.markFailedNoRetry(inbox, errorMessage);
                    
                    // 记录到失败日志表（未匹配的事件类型需要人工处理）
                    webhookFailLogService.saveFailLog(request, "VALIDATION", errorMessage, true);
                    
                    monitorService.recordUnmatched(inbox.getEventType());
                    continue;
                }
                WebhookEventHandler handler = handlerOpt.get();
                monitorService.recordMatched(inbox.getEventType(), handler.getClass().getSimpleName());

                handler.handle(inbox, request);
                inboxService.markSuccess(inbox);
                monitorService.recordSuccess(inbox.getEventType(), System.currentTimeMillis() - start);
                success++;
            } catch (Exception ex) {
                long cost = System.currentTimeMillis() - start;
                log.error("Webhook 收件箱处理失败: messageId={}, eventType={}",
                        inbox.getMessageId(), inbox.getEventType(), ex);
                
                inboxService.markFailed(inbox, ex.getMessage());
                
                // 如果超过最大重试次数，记录到失败日志表
                if (inboxService.reachMaxRetry(inbox)) {
                    // 如果 request 为 null，重新构建
                    if (request == null) {
                        request = objectMapper.convertValue(inbox.getPayload(), WebhookRequest.class);
                        request.setMessageId(inbox.getMessageId());
                        request.setDeviceCode(inbox.getDeviceCode());
                        request.setDeviceId(inbox.getTbDeviceId());
                        request.setEventType(inbox.getEventType());
                        request.setWebhookCategory(inbox.getWebhookCategory());
                    }
                    
                    // 判断是否需要人工处理：业务异常（ServiceException）需要人工处理，系统异常可以自动重试
                    boolean needManual = ex instanceof ServiceException;
                    webhookFailLogService.saveFailLog(request, "PROCESS", ex.getMessage(), needManual);
                }
                
                monitorService.recordFailure(inbox.getEventType(), ex.getMessage(), cost, true);
            }
        }
        return success;
    }
}

