package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.common.enums.InboxStatusEnum;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Webhook 消息处理服务（核心处理逻辑）
 * 负责从收件箱消息构建请求、查找 Handler、调用处理、状态管理等核心业务逻辑
 * 
 * 使用场景：
 * 1. 实时处理：WebhookReceiveService 异步调用
 * 2. 定时任务：WebhookProcessWorker 批量调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessService {
    
    private final WebhookInboxService inboxService;
    private final WebhookHandlerRegistry handlerRegistry;
    private final WebhookMonitorService monitorService;
    private final WebhookFailLogService webhookFailLogService;
    private final ObjectMapper objectMapper;
    
    /**
     * 处理单条收件箱消息（核心处理逻辑）
     * 
     * 注意：调用此方法前，消息状态应该已经被标记为 PROCESSING（通过乐观锁保证幂等性）
     * 
     * @param inbox 收件箱消息（状态应为 PROCESSING）
     */
    public void processSingle(WebhookInboxDO inbox) {
        long start = System.currentTimeMillis();
        WebhookRequest request = null;
        try {
            log.debug("[Webhook-Process] ====== 开始处理消息 ======");
            log.debug("[Webhook-Process] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}", 
                inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(), 
                inbox.getStatus(), inbox.getProcessCount());
            
            // 重新查询最新状态，确保状态正确
            WebhookInboxDO latestInbox = inboxService.findByMessageId(inbox.getMessageId());
            if (latestInbox == null) {
                log.warn("[Webhook-Process] 消息不存在: messageId={}", inbox.getMessageId());
                return;
            }
            
            // 检查状态：只有 PROCESSING 状态才处理（避免重复处理）
            if (!InboxStatusEnum.PROCESSING.name().equals(latestInbox.getStatus())) {
                log.debug("[Webhook-Process] 消息状态不是PROCESSING，跳过处理: messageId={}, status={}", 
                    inbox.getMessageId(), latestInbox.getStatus());
                return;
            }
            
            // 构建 WebhookRequest
            request = buildWebhookRequest(latestInbox);
            
            // 查找 Handler
            log.debug("[Webhook-Process] 查找Handler: eventType={}", latestInbox.getEventType());
            Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(latestInbox.getEventType());
            if (handlerOpt.isEmpty()) {
                handleUnmatchedHandler(latestInbox, request);
                return;
            }
            
            WebhookEventHandler handler = handlerOpt.get();
            log.debug("[Webhook-Process] 找到Handler: eventType={}, handlerClass={}", 
                latestInbox.getEventType(), handler.getClass().getSimpleName());
            
            monitorService.recordMatched(latestInbox.getEventType(), handler.getClass().getSimpleName());
            
            // 调用 Handler 处理
            log.debug("[Webhook-Process] 调用Handler处理: handler={}, messageId={}", 
                handler.getClass().getSimpleName(), latestInbox.getMessageId());
            handler.handle(latestInbox, request);
            log.debug("[Webhook-Process] Handler处理完成: handler={}, messageId={}", 
                handler.getClass().getSimpleName(), latestInbox.getMessageId());
            
            // 标记为成功
            boolean marked = inboxService.markSuccess(latestInbox);
            if (!marked) {
                log.warn("[Webhook-Process] 标记成功失败，消息可能已被其他线程处理: messageId={}", latestInbox.getMessageId());
            }
            
            long cost = System.currentTimeMillis() - start;
            monitorService.recordSuccess(latestInbox.getEventType(), cost);
            log.info("[Webhook-Process] ====== 消息处理成功 ====== messageId={}, 耗时: {}ms", 
                latestInbox.getMessageId(), cost);
                
        } catch (Exception ex) {
            handleProcessException(inbox, request, ex, start);
        }
    }
    
    /**
     * 从收件箱消息构建 WebhookRequest
     */
    private WebhookRequest buildWebhookRequest(WebhookInboxDO inbox) {
        WebhookRequest request = objectMapper.convertValue(inbox.getPayload(), WebhookRequest.class);
        
        // 填充基础字段
        request.setMessageId(inbox.getMessageId());
        request.setDeviceCode(inbox.getDeviceCode());
        request.setDeviceId(inbox.getTbDeviceId());
        request.setEventType(inbox.getEventType());
        request.setWebhookCategory(inbox.getWebhookCategory());
        
        // 如果时间戳为空，尝试从 telemetryData 中提取
        if (request.getTimestamp() == null && request.getDataTimestamp() == null 
                && request.getTelemetryData() != null) {
            Object telemetryTimestamp = request.getTelemetryData().get("timestamp");
            if (telemetryTimestamp != null) {
                try {
                    // 支持字符串和数字格式的时间戳
                    if (telemetryTimestamp instanceof String) {
                        request.setTimestamp(Long.parseLong((String) telemetryTimestamp));
                    } else if (telemetryTimestamp instanceof Number) {
                        request.setTimestamp(((Number) telemetryTimestamp).longValue());
                    }
                } catch (Exception e) {
                    log.warn("[Webhook-Process] 从telemetryData提取timestamp失败: messageId={}, timestamp={}", 
                        inbox.getMessageId(), telemetryTimestamp, e);
                }
            }
        }
        
        
        log.debug("[Webhook-Process] 构建请求对象: messageId={}, deviceCode={}, deviceId={}, eventType={}, category={}, timestamp={}, dataTimestamp={}", 
            request.getMessageId(), request.getDeviceCode(), request.getDeviceId(), 
            request.getEventType(), request.getWebhookCategory(), request.getTimestamp(), request.getDataTimestamp());
        
        return request;
    }
    
    /**
     * 处理未匹配到 Handler 的情况
     */
    private void handleUnmatchedHandler(WebhookInboxDO inbox, WebhookRequest request) {
        String errorMessage = "Unsupported eventType: " + inbox.getEventType();
        log.warn("[Webhook-Process] 未找到匹配的Handler: eventType={}, messageId={}", 
            inbox.getEventType(), inbox.getMessageId());
        
        // 使用乐观锁标记为失败（不可重试），如果失败说明已被其他线程处理
        boolean marked = inboxService.markFailedNoRetry(inbox, errorMessage);
        if (!marked) {
            log.warn("[Webhook-Process] 标记失败失败，消息可能已被其他线程处理: messageId={}", inbox.getMessageId());
        }
        
        // 记录到失败日志表（未匹配的事件类型需要人工处理）
        webhookFailLogService.saveFailLog(request, "VALIDATION", errorMessage, true);
        
        monitorService.recordUnmatched(inbox.getEventType());
    }
    
    /**
     * 处理处理过程中的异常
     */
    private void handleProcessException(WebhookInboxDO inbox, WebhookRequest request, Exception ex, long start) {
        long cost = System.currentTimeMillis() - start;
        log.error("[Webhook-Process] ====== 消息处理失败 ====== messageId={}, eventType={}, 耗时: {}ms", 
            inbox.getMessageId(), inbox.getEventType(), cost, ex);
        
        // 重新查询最新状态
        WebhookInboxDO latestInbox = inboxService.findByMessageId(inbox.getMessageId());
        if (latestInbox != null) {
            // 使用乐观锁标记为失败，如果失败说明已被其他线程处理
            boolean marked = inboxService.markFailed(latestInbox, ex.getMessage());
            if (!marked) {
                log.warn("[Webhook-Process] 标记失败失败，消息可能已被其他线程处理: messageId={}", latestInbox.getMessageId());
            } else {
                log.debug("[Webhook-Process] 已标记为失败状态: messageId={}, error={}, processCount={}", 
                    latestInbox.getMessageId(), ex.getMessage(), latestInbox.getProcessCount());
            }
            
            // 如果超过最大重试次数，记录到失败日志表
            if (inboxService.reachMaxRetry(latestInbox)) {
                log.warn("[Webhook-Process] 达到最大重试次数: messageId={}, processCount={}", 
                    latestInbox.getMessageId(), latestInbox.getProcessCount());
                
                // 如果 request 为 null，重新构建
                if (request == null) {
                    request = objectMapper.convertValue(latestInbox.getPayload(), WebhookRequest.class);
                    request.setMessageId(latestInbox.getMessageId());
                    request.setDeviceCode(latestInbox.getDeviceCode());
                    request.setDeviceId(latestInbox.getTbDeviceId());
                    request.setEventType(latestInbox.getEventType());
                    request.setWebhookCategory(latestInbox.getWebhookCategory());
                }
                
                // 判断是否需要人工处理
                boolean needManual = determineNeedManual(ex);
                log.debug("[Webhook-Process] 保存失败日志: messageId={}, needManual={}, errorType={}", 
                    latestInbox.getMessageId(), needManual, ex.getClass().getSimpleName());
                webhookFailLogService.saveFailLog(request, "PROCESS", ex.getMessage(), needManual);
            }
        }
        
        monitorService.recordFailure(inbox.getEventType(), ex.getMessage(), cost, true);
    }
    
    /**
     * 判断是否需要人工处理
     */
    private boolean determineNeedManual(Exception ex) {
        // 1. 系统异常可以自动重试（needManual = false）
        // 2. 业务异常（IotPortalException）需要区分：
        //    - 可重试的业务异常（如：并发锁冲突）-> needManual = false
        //    - 不可重试的业务异常（如：数据校验失败）-> needManual = true
        if (ex instanceof IotPortalException) {
            String errorMessage = ex.getMessage();
            // 可重试的业务异常：并发锁冲突、临时资源不可用等
            if (errorMessage != null && (
                errorMessage.contains("正在处理中") || 
                errorMessage.contains("请稍后重试") ||
                errorMessage.contains("并发处理")
            )) {
                return false; // 可重试
            } else {
                return true; // 需要人工处理
            }
        }
        return false; // 系统异常，可重试
    }
}

