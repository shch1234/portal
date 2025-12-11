package com.weili.iot_portal.task.webhook;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Webhook 收件箱批量处理 Worker（用于定时任务）
 * 
 * 职责：
 * 1. 批量查询待处理消息
 * 2. 使用乐观锁标记为处理中
 * 3. 调用 WebhookProcessService 处理单条消息
 * 
 * 使用场景：
 * - 定时任务（WebhookInboxJob）调用 processBatch() 批量处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessWorker {

    private final WebhookInboxService inboxService;
    private final WebhookProcessService webhookProcessService;

    /**
     * 批量处理待处理消息（用于定时任务）
     * 
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
            try {
                log.debug("[Webhook-Worker] ====== 开始处理单条消息 ======");
                log.debug("[Webhook-Worker] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}", 
                    inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(), 
                    inbox.getStatus(), inbox.getProcessCount());
                
                // 使用乐观锁标记为处理中，如果失败说明已被其他线程处理，跳过
                boolean marked = inboxService.markProcessing(inbox);
                if (!marked) {
                    log.debug("[Webhook-Worker] 消息已被其他线程处理，跳过: messageId={}", inbox.getMessageId());
                    continue;
                }
                log.debug("[Webhook-Worker] 已标记为处理中状态");
                
                // 重新查询最新状态的消息对象
                WebhookInboxDO processingInbox = inboxService.findByMessageId(inbox.getMessageId());
                if (processingInbox == null || !"PROCESSING".equals(processingInbox.getStatus())) {
                    log.debug("[Webhook-Worker] 消息状态异常，跳过处理: messageId={}, status={}", 
                        inbox.getMessageId(), processingInbox != null ? processingInbox.getStatus() : "null");
                    continue;
                }
                
                // 调用处理服务处理单条消息（核心处理逻辑）
                webhookProcessService.processSingle(processingInbox);
                success++;
                
            } catch (Exception ex) {
                log.error("[Webhook-Worker] 处理消息异常: messageId={}, eventType={}", 
                    inbox.getMessageId(), inbox.getEventType(), ex);
                // 异常已在 WebhookProcessService 中处理（状态标记、失败日志等）
            }
        }
        
        long batchCost = System.currentTimeMillis() - batchStartTime;
        log.debug("[Webhook-Worker] ====== 批量处理完成 ====== 成功: {}/{}, 总耗时: {}ms", 
            success, inboxList.size(), batchCost);
        return success;
    }
}

