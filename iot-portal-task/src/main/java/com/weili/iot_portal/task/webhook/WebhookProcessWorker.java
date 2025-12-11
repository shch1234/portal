package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookMonitorService;
import com.weili.iot_portal.service.ingestion.support.WebhookTimestampUtils;
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
        long batchStartTime = System.currentTimeMillis();
        log.debug("[Webhook-Worker] ====== 开始批量处理收件箱消息 ======");
        List<WebhookInboxDO> inboxList = inboxService.fetchDue();
        log.debug("[Webhook-Worker] 查询到待处理消息数: {}", inboxList.size());
        
        if (inboxList.isEmpty()) {
            return 0;
        }
        
        int success = 0;
        for (WebhookInboxDO inbox : inboxList) {
            long start = System.currentTimeMillis();
            WebhookRequest request = null;
            boolean marked;  // 在循环开始处声明，避免重复定义
            try {
                log.debug("[Webhook-Worker] ====== 开始处理单条消息 ======");
                log.debug("[Webhook-Worker] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}", 
                    inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(), 
                    inbox.getStatus(), inbox.getProcessCount());
                
                // 使用乐观锁标记为处理中，如果失败说明已被其他线程处理，跳过
                marked = inboxService.markProcessing(inbox);
                if (!marked) {
                    log.debug("[Webhook-Worker] 消息已被其他线程处理，跳过: messageId={}", inbox.getMessageId());
                    continue;
                }
                log.debug("[Webhook-Worker] 已标记为处理中状态");
                
                request = objectMapper.convertValue(inbox.getPayload(), WebhookRequest.class);
                // 填充基础字段
                request.setMessageId(inbox.getMessageId());
                request.setDeviceCode(inbox.getDeviceCode());
                request.setDeviceId(inbox.getTbDeviceId());
                request.setEventType(inbox.getEventType());
                request.setWebhookCategory(inbox.getWebhookCategory());
                
                // 统一转换时间戳：ThingsBoard 发送的是毫秒，统一转换为秒
                WebhookTimestampUtils.normalizeTimestamp(request);
                
                log.debug("[Webhook-Worker] 构建请求对象: messageId={}, deviceCode={}, deviceId={}, eventType={}, category={}", 
                    request.getMessageId(), request.getDeviceCode(), request.getDeviceId(), 
                    request.getEventType(), request.getWebhookCategory());
                
                if (log.isDebugEnabled()) {
                    log.debug("[Webhook-Worker] 事件数据: eventData={}", 
                        request.getEventData() != null ? request.getEventData().toString() : "null");
                    log.debug("[Webhook-Worker] 遥测数据: telemetryData={}", 
                        request.getTelemetryData() != null ? request.getTelemetryData().toString() : "null");
                }

                log.debug("[Webhook-Worker] 查找Handler: eventType={}", inbox.getEventType());
                Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(inbox.getEventType());
                if (handlerOpt.isEmpty()) {
                    // 未匹配到 Handler，直接标记失败（不进入重复重试）并告警
                    String errorMessage = "Unsupported eventType: " + inbox.getEventType();
                    log.warn("[Webhook-Worker] 未找到匹配的Handler: eventType={}, messageId={}", 
                        inbox.getEventType(), inbox.getMessageId());
                    
                    // 使用乐观锁标记为失败（不可重试），如果失败说明已被其他线程处理
                    marked = inboxService.markFailedNoRetry(inbox, errorMessage);
                    if (!marked) {
                        log.warn("[Webhook-Worker] 标记失败失败，消息可能已被其他线程处理: messageId={}", inbox.getMessageId());
                    }
                    
                    // 记录到失败日志表（未匹配的事件类型需要人工处理）
                    webhookFailLogService.saveFailLog(request, "VALIDATION", errorMessage, true);
                    
                    monitorService.recordUnmatched(inbox.getEventType());
                    continue;
                }
                
                WebhookEventHandler handler = handlerOpt.get();
                log.debug("[Webhook-Worker] 找到Handler: eventType={}, handlerClass={}", 
                    inbox.getEventType(), handler.getClass().getSimpleName());
                
                monitorService.recordMatched(inbox.getEventType(), handler.getClass().getSimpleName());

                log.debug("[Webhook-Worker] 调用Handler处理: handler={}, messageId={}", 
                    handler.getClass().getSimpleName(), inbox.getMessageId());
                handler.handle(inbox, request);
                log.debug("[Webhook-Worker] Handler处理完成: handler={}, messageId={}", 
                    handler.getClass().getSimpleName(), inbox.getMessageId());
                
                // 使用乐观锁标记为成功，如果失败说明已被其他线程处理
                marked = inboxService.markSuccess(inbox);
                if (!marked) {
                    log.warn("[Webhook-Worker] 标记成功失败，消息可能已被其他线程处理: messageId={}", inbox.getMessageId());
                }
                long cost = System.currentTimeMillis() - start;
                monitorService.recordSuccess(inbox.getEventType(), cost);
                log.debug("[Webhook-Worker] ====== 消息处理成功 ====== messageId={}, 耗时: {}ms", 
                    inbox.getMessageId(), cost);
                success++;
            } catch (Exception ex) {
                long cost = System.currentTimeMillis() - start;
                log.error("[Webhook-Worker] ====== 消息处理失败 ====== messageId={}, eventType={}, 耗时: {}ms", 
                    inbox.getMessageId(), inbox.getEventType(), cost, ex);
                
                // 使用乐观锁标记为失败，如果失败说明已被其他线程处理
                marked = inboxService.markFailed(inbox, ex.getMessage());
                if (!marked) {
                    log.warn("[Webhook-Worker] 标记失败失败，消息可能已被其他线程处理: messageId={}", inbox.getMessageId());
                } else {
                    log.debug("[Webhook-Worker] 已标记为失败状态: messageId={}, error={}, processCount={}", 
                        inbox.getMessageId(), ex.getMessage(), inbox.getProcessCount());
                }
                
                // 如果超过最大重试次数，记录到失败日志表
                if (inboxService.reachMaxRetry(inbox)) {
                    log.warn("[Webhook-Worker] 达到最大重试次数: messageId={}, processCount={}", 
                        inbox.getMessageId(), inbox.getProcessCount());
                    
                    // 如果 request 为 null，重新构建
                    if (request == null) {
                        request = objectMapper.convertValue(inbox.getPayload(), WebhookRequest.class);
                        request.setMessageId(inbox.getMessageId());
                        request.setDeviceCode(inbox.getDeviceCode());
                        request.setDeviceId(inbox.getTbDeviceId());
                        request.setEventType(inbox.getEventType());
                        request.setWebhookCategory(inbox.getWebhookCategory());
                    }
                    
                    // 判断是否需要人工处理：
                    // 1. 系统异常可以自动重试（needManual = false）
                    // 2. 业务异常（ServiceException）需要区分：
                    //    - 可重试的业务异常（如：并发锁冲突）-> needManual = false
                    //    - 不可重试的业务异常（如：数据校验失败）-> needManual = true
                    boolean needManual = false;
                    if (ex instanceof ServiceException) {
                        String errorMessage = ex.getMessage();
                        // 可重试的业务异常：并发锁冲突、临时资源不可用等
                        if (errorMessage != null && (
                            errorMessage.contains("正在处理中") || 
                            errorMessage.contains("请稍后重试") ||
                            errorMessage.contains("并发处理")
                        )) {
                            needManual = false; // 可重试
                        } else {
                            needManual = true; // 需要人工处理
                        }
                    }
                    log.debug("[Webhook-Worker] 保存失败日志: messageId={}, needManual={}, errorType={}", 
                        inbox.getMessageId(), needManual, ex.getClass().getSimpleName());
                    webhookFailLogService.saveFailLog(request, "PROCESS", ex.getMessage(), needManual);
                }
                
                monitorService.recordFailure(inbox.getEventType(), ex.getMessage(), cost, true);
            }
        }
        
        long batchCost = System.currentTimeMillis() - batchStartTime;
        log.debug("[Webhook-Worker] ====== 批量处理完成 ====== 成功: {}/{}, 总耗时: {}ms", 
            success, inboxList.size(), batchCost);
        return success;
    }
    
    /**
     * 处理单条消息（用于实时异步处理）
     * 注意：调用此方法前，消息状态应该已经被标记为 PROCESSING（通过乐观锁保证幂等性）
     * 
     * @param inbox 收件箱消息
     */
    public void processSingle(WebhookInboxDO inbox) {
        long start = System.currentTimeMillis();
        WebhookRequest request = null;
        try {
            log.info("[Webhook-Worker] ====== 开始处理单条消息（实时） ======");
            log.info("[Webhook-Worker] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}", 
                inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(), 
                inbox.getStatus(), inbox.getProcessCount());
            
            // 使用传入的 inbox 对象（已经在异步方法中标记为 PROCESSING）
            // 重新查询最新状态，确保状态正确
            WebhookInboxDO latestInbox = inboxService.findByMessageId(inbox.getMessageId());
            if (latestInbox == null) {
                log.warn("[Webhook-Worker] 消息不存在: messageId={}", inbox.getMessageId());
                return;
            }
            
            // 检查状态：只有 PROCESSING 状态才处理（避免重复处理）
            if (!"PROCESSING".equals(latestInbox.getStatus())) {
                log.debug("[Webhook-Worker] 消息状态不是PROCESSING，跳过处理: messageId={}, status={}", 
                    inbox.getMessageId(), latestInbox.getStatus());
                return;
            }
            
            request = objectMapper.convertValue(latestInbox.getPayload(), WebhookRequest.class);
            // 填充基础字段
            request.setMessageId(latestInbox.getMessageId());
            request.setDeviceCode(latestInbox.getDeviceCode());
            request.setDeviceId(latestInbox.getTbDeviceId());
            request.setEventType(latestInbox.getEventType());
            request.setWebhookCategory(latestInbox.getWebhookCategory());
            
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
                        log.warn("[Webhook-Worker] 从telemetryData提取timestamp失败: messageId={}, timestamp={}", 
                            latestInbox.getMessageId(), telemetryTimestamp, e);
                    }
                }
            }
            
            // 统一转换时间戳：ThingsBoard 发送的是毫秒，统一转换为秒
            WebhookTimestampUtils.normalizeTimestamp(request);
            
            log.debug("[Webhook-Worker] 构建请求对象: messageId={}, deviceCode={}, deviceId={}, eventType={}, category={}, timestamp={}, dataTimestamp={}", 
                request.getMessageId(), request.getDeviceCode(), request.getDeviceId(), 
                request.getEventType(), request.getWebhookCategory(), request.getTimestamp(), request.getDataTimestamp());

            log.debug("[Webhook-Worker] 查找Handler: eventType={}", latestInbox.getEventType());
            Optional<WebhookEventHandler> handlerOpt = handlerRegistry.resolve(latestInbox.getEventType());
            if (handlerOpt.isEmpty()) {
                // 未匹配到 Handler，直接标记失败（不进入重复重试）并告警
                String errorMessage = "Unsupported eventType: " + latestInbox.getEventType();
                log.warn("[Webhook-Worker] 未找到匹配的Handler: eventType={}, messageId={}", 
                    latestInbox.getEventType(), latestInbox.getMessageId());
                
                // 使用乐观锁标记为失败（不可重试），如果失败说明已被其他线程处理
                boolean marked = inboxService.markFailedNoRetry(latestInbox, errorMessage);
                if (!marked) {
                    log.warn("[Webhook-Worker] 标记失败失败，消息可能已被其他线程处理: messageId={}", latestInbox.getMessageId());
                }
                
                // 记录到失败日志表（未匹配的事件类型需要人工处理）
                webhookFailLogService.saveFailLog(request, "VALIDATION", errorMessage, true);
                
                monitorService.recordUnmatched(latestInbox.getEventType());
                return;
            }
            
            WebhookEventHandler handler = handlerOpt.get();
            log.debug("[Webhook-Worker] 找到Handler: eventType={}, handlerClass={}", 
                latestInbox.getEventType(), handler.getClass().getSimpleName());
            
            monitorService.recordMatched(latestInbox.getEventType(), handler.getClass().getSimpleName());

            log.debug("[Webhook-Worker] 调用Handler处理: handler={}, messageId={}", 
                handler.getClass().getSimpleName(), latestInbox.getMessageId());
            handler.handle(latestInbox, request);
            log.debug("[Webhook-Worker] Handler处理完成: handler={}, messageId={}", 
                handler.getClass().getSimpleName(), latestInbox.getMessageId());
            
            // 使用乐观锁标记为成功，如果失败说明已被其他线程处理
            boolean marked = inboxService.markSuccess(latestInbox);
            if (!marked) {
                log.warn("[Webhook-Worker] 标记成功失败，消息可能已被其他线程处理: messageId={}", latestInbox.getMessageId());
            }
            long cost = System.currentTimeMillis() - start;
            monitorService.recordSuccess(latestInbox.getEventType(), cost);
            log.info("[Webhook-Worker] ====== 消息处理成功（实时） ====== messageId={}, 耗时: {}ms", 
                latestInbox.getMessageId(), cost);
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - start;
            log.error("[Webhook-Worker] ====== 消息处理失败（实时） ====== messageId={}, eventType={}, 耗时: {}ms", 
                inbox.getMessageId(), inbox.getEventType(), cost, ex);
            
            // 重新查询最新状态
            WebhookInboxDO latestInbox = inboxService.findByMessageId(inbox.getMessageId());
            if (latestInbox != null) {
                // 使用乐观锁标记为失败，如果失败说明已被其他线程处理
                boolean marked = inboxService.markFailed(latestInbox, ex.getMessage());
                if (!marked) {
                    log.warn("[Webhook-Worker] 标记失败失败，消息可能已被其他线程处理: messageId={}", latestInbox.getMessageId());
                } else {
                    log.debug("[Webhook-Worker] 已标记为失败状态: messageId={}, error={}, processCount={}", 
                        latestInbox.getMessageId(), ex.getMessage(), latestInbox.getProcessCount());
                }
                
                // 如果超过最大重试次数，记录到失败日志表
                if (inboxService.reachMaxRetry(latestInbox)) {
                    log.warn("[Webhook-Worker] 达到最大重试次数: messageId={}, processCount={}", 
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
                    
                    // 判断是否需要人工处理：
                    // 1. 系统异常可以自动重试（needManual = false）
                    // 2. 业务异常（ServiceException）需要区分：
                    //    - 可重试的业务异常（如：并发锁冲突）-> needManual = false
                    //    - 不可重试的业务异常（如：数据校验失败）-> needManual = true
                    boolean needManual = false;
                    if (ex instanceof ServiceException) {
                        String errorMessage = ex.getMessage();
                        // 可重试的业务异常：并发锁冲突、临时资源不可用等
                        if (errorMessage != null && (
                            errorMessage.contains("正在处理中") || 
                            errorMessage.contains("请稍后重试") ||
                            errorMessage.contains("并发处理")
                        )) {
                            needManual = false; // 可重试
                        } else {
                            needManual = true; // 需要人工处理
                        }
                    }
                    log.debug("[Webhook-Worker] 保存失败日志: messageId={}, needManual={}, errorType={}", 
                        latestInbox.getMessageId(), needManual, ex.getClass().getSimpleName());
                    webhookFailLogService.saveFailLog(request, "PROCESS", ex.getMessage(), needManual);
                }
            }
            
            monitorService.recordFailure(inbox.getEventType(), ex.getMessage(), cost, true);
        }
    }
}

