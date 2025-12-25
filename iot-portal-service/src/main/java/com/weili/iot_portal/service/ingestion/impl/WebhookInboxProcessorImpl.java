package com.weili.iot_portal.service.ingestion.impl;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.service.ingestion.WebhookInboxProcessor;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookProcessService;
import com.weili.iot_portal.domain.ingestion.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Webhook 收件箱批处理实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookInboxProcessorImpl implements WebhookInboxProcessor {

    private final WebhookInboxService inboxService;
    private final WebhookProcessService webhookProcessService;

    @Override
    public ProcessResult processBatch() {
        long batchStartTime = System.currentTimeMillis();
        log.debug("[Webhook-Processor] ====== 开始批量处理收件箱消息 ======");
        List<WebhookInboxDO> inboxList = inboxService.fetchDue();
        log.debug("[Webhook-Processor] 查询到待处理消息数: {}", inboxList.size());

        if (inboxList.isEmpty()) {
            return ProcessResult.empty();
        }

        int success = 0;
        int skip = 0;
        int error = 0;

        for (WebhookInboxDO inbox : inboxList) {
            try {
                log.debug("[Webhook-Processor] ====== 开始处理单条消息 ======");
                log.debug("[Webhook-Processor] messageId={}, eventType={}, deviceCode={}, status={}, processCount={}",
                        inbox.getMessageId(), inbox.getEventType(), inbox.getDeviceCode(),
                        inbox.getStatus(), inbox.getProcessCount());

                // 使用乐观锁标记为处理中，如果失败说明已被其他线程处理，跳过
                boolean marked = inboxService.markProcessing(inbox);
                if (!marked) {
                    log.debug("[Webhook-Processor] 消息已被其他线程处理，跳过: messageId={}", inbox.getMessageId());
                    skip++;
                    continue;
                }
                log.debug("[Webhook-Processor] 已标记为处理中状态");

                // 重新查询最新状态的消息对象
                WebhookInboxDO processingInbox = inboxService.findByMessageId(inbox.getMessageId());
                if (processingInbox == null || !"PROCESSING".equals(processingInbox.getStatus())) {
                    log.debug("[Webhook-Processor] 消息状态异常，跳过处理: messageId={}, status={}",
                            inbox.getMessageId(), processingInbox != null ? processingInbox.getStatus() : "null");
                    skip++;
                    continue;
                }

                // 调用处理服务处理单条消息（核心处理逻辑）
                webhookProcessService.processSingle(processingInbox);
                success++;

            } catch (Exception ex) {
                log.error("[Webhook-Processor] 处理消息异常: messageId={}, eventType={}",
                        inbox.getMessageId(), inbox.getEventType(), ex);
                error++;
                // 具体状态标记、失败日志等逻辑在 WebhookProcessService 中处理
            }
        }

        long batchCost = System.currentTimeMillis() - batchStartTime;
        log.debug("[Webhook-Processor] ====== 批量处理完成 ====== 成功: {}, 跳过: {}, 失败: {}, 总数: {}, 总耗时: {}ms",
                success, skip, error, inboxList.size(), batchCost);
        return new ProcessResult(success, skip, error);
    }
}

