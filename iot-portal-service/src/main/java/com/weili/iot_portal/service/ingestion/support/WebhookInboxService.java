package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.common.enums.InboxStatusEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.ingestion.WebhookInboxRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookInboxService {

    private final WebhookInboxRepository inboxRepository;

    @Value("${webhook.inbox.batch-size:100}")
    private int batchSize;
    
    /**
     * 失败消息的最大处理比例（0.0-1.0）
     * 例如：0.3 表示每次批量处理时，最多30%是失败重试的消息，70%是新消息
     * 这样可以确保新消息优先处理，避免被旧失败消息阻塞
     */
    @Value("${webhook.inbox.failed-message-ratio:0.3}")
    private double failedMessageRatio;

    @Value("${webhook.inbox.max-retry-count:5}")
    private int maxRetryCount;

    @Value("${webhook.inbox.retry-interval-base-seconds:60}")
    private long retryIntervalBaseSeconds;

    public void saveToInbox(WebhookRequest request) {
        log.debug("[Webhook-Inbox] ====== 保存消息到收件箱 ======");
        log.debug("[Webhook-Inbox] messageId={}, eventType={}, deviceCode={}, deviceId={}", 
            request.getMessageId(), request.getEventType(), request.getDeviceCode(), request.getDeviceId());
        
        if (request == null || StringUtils.isBlank(request.getMessageId())) {
            throw new IotPortalException(IotPortalErrorCode.WEBHOOK_MESSAGE_ID_MISSING);
        }
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        payload.put("telemetryData", request.getTelemetryData());
        payload.put("metadata", request.getMetadata());
        payload.put("transactionInfo", request.getTransactionInfo());
        if (request.getTimestamp() != null) {
            payload.put("timestamp", request.getTimestamp());
        }
        if (request.getDataTimestamp() != null) {
            payload.put("dataTimestamp", request.getDataTimestamp());
        }
        
        log.debug("[Webhook-Inbox] 构建payload: eventData={}, telemetryData={}, metadata={}, transactionInfo={}", 
            request.getEventData() != null, request.getTelemetryData() != null, 
            request.getMetadata() != null, request.getTransactionInfo() != null);

        WebhookInboxDO inbox = new WebhookInboxDO();
        inbox.setMessageId(request.getMessageId());
        inbox.setTbDeviceId(request.getDeviceId());
        inbox.setDeviceCode(request.getDeviceCode());
        inbox.setEventType(request.getEventType());
        inbox.setWebhookCategory(request.getWebhookCategory());
        inbox.setPayload(payload);
        inbox.setStatus(InboxStatusEnum.PENDING.name());
        inbox.setProcessCount(0);
        inbox.setReceivedTime(LocalDateTime.now());
        log.debug("[Webhook-Inbox] 插入收件箱记录: messageId={}, status={}, receivedTime={}", 
            inbox.getMessageId(), inbox.getStatus(), inbox.getReceivedTime());
        inboxRepository.insert(inbox);
        log.debug("[Webhook-Inbox] ====== 消息已保存到收件箱 ====== messageId={}", inbox.getMessageId());
    }

    /**
     * 使用乐观锁标记为处理中（用于异步处理）
     * 只有 PENDING 状态才能更新为 PROCESSING，返回更新的记录数
     * 
     * @param messageId 消息ID
     * @return 更新的记录数（0表示状态不符合条件，已被其他线程处理）
     */
    public int markProcessingWithLock(String messageId) {
        return inboxRepository.markProcessingWithLock(messageId);
    }

    /**
     * 根据 messageId 查询消息
     */
    public WebhookInboxDO findByMessageId(String messageId) {
        return inboxRepository.findByMessageId(messageId);
    }

    /**
     * 查询待处理/可重试的记录（优化策略：优先处理新消息）
     * 
     * 处理策略：
     * 1. 优先处理新消息（PENDING状态），按接收时间升序
     * 2. 失败消息（FAILED状态）按重试时间升序，且限制比例（避免阻塞新消息）
     * 3. 确保新消息优先处理，失败消息按重试时间延迟处理
     * 
     * 幂等性保证：
     * - 只查询 PENDING 和 FAILED 状态的消息，不包含 SUCCESS 状态
     * - 已处理成功（SUCCESS）的消息永远不会被重复查询和处理
     * - 通过状态字段确保昨天处理过的消息今天不会被重复处理
     * 
     * @return 待处理的消息列表（新消息优先，失败消息按比例限制）
     */
    public List<WebhookInboxDO> fetchDue() {
        LocalDateTime now = LocalDateTime.now();
        List<WebhookInboxDO> result = new java.util.ArrayList<>();
        
        // 1. 优先查询新消息（PENDING状态），按接收时间升序
        int pendingLimit = (int) (batchSize * (1 - failedMessageRatio));
        List<WebhookInboxDO> pendingMessages = inboxRepository.fetchPendingMessages(pendingLimit);
        result.addAll(pendingMessages);
        log.debug("[Webhook-Inbox] 查询到新消息数: {}", pendingMessages.size());
        
        // 2. 查询失败消息（FAILED状态），按重试时间升序，限制数量
        int failedLimit = batchSize - result.size();
        if (failedLimit > 0) {
            List<WebhookInboxDO> failedMessages = inboxRepository.fetchFailedMessages(failedLimit, now);
            result.addAll(failedMessages);
            log.debug("[Webhook-Inbox] 查询到失败重试消息数: {}", failedMessages.size());
        }
        
        log.debug("[Webhook-Inbox] 总计查询到待处理消息数: {} (新消息: {}, 失败重试: {})", 
            result.size(), pendingMessages.size(), result.size() - pendingMessages.size());
        return result;
    }
    
    /**
     * 查询指定设备正在处理中的消息（用于诊断锁冲突）
     * 
     * @param deviceCode 设备编码
     * @param excludeMessageId 排除的消息ID（通常是当前消息本身）
     * @return 正在处理中的消息列表（不包含excludeMessageId）
     */
    public List<WebhookInboxDO> findProcessingByDevice(String deviceCode, String excludeMessageId) {
        if (StringUtils.isBlank(deviceCode)) {
            return java.util.Collections.emptyList();
        }
        return inboxRepository.findProcessingByDevice(deviceCode, excludeMessageId);
    }
    
    /**
     * 查询指定设备相同时间戳的待处理消息（用于诊断相同时间戳的并发问题）
     * 
     * @param deviceCode 设备编码
     * @param dataTimestamp 数据时间戳
     * @param excludeMessageId 排除的消息ID（通常是当前消息本身）
     * @return 相同时间戳的待处理消息列表
     */
    public List<WebhookInboxDO> findPendingByDeviceAndTimestamp(String deviceCode, Long dataTimestamp, String excludeMessageId) {
        if (StringUtils.isBlank(deviceCode) || dataTimestamp == null) {
            return java.util.Collections.emptyList();
        }
        
        // 从 payload 中查询相同 dataTimestamp 的消息
        // 注意：这里需要从 payload 的 JSON 中提取 dataTimestamp，所以需要查询所有 PENDING 和 PROCESSING 状态的消息
        List<WebhookInboxDO> allMessages = inboxRepository.findPendingOrProcessingByDevice(deviceCode, excludeMessageId);
        
        // 从 payload 中提取 dataTimestamp 并过滤
        List<WebhookInboxDO> result = new java.util.ArrayList<>();
        for (WebhookInboxDO message : allMessages) {
            try {
                Map<String, Object> payload = message.getPayload();
                if (payload != null) {
                    Object payloadDataTimestamp = payload.get("dataTimestamp");
                    if (payloadDataTimestamp != null) {
                        long payloadTs = payloadDataTimestamp instanceof Number 
                            ? ((Number) payloadDataTimestamp).longValue() 
                            : Long.parseLong(payloadDataTimestamp.toString());
                        if (payloadTs == dataTimestamp) {
                            result.add(message);
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        
        return result;
    }

    /**
     * 标记为处理中（使用乐观锁，确保幂等性）
     * 只有 PENDING 或 FAILED 状态才能更新为 PROCESSING，避免重复处理已成功或正在处理的消息
     * 
     * @param inbox 收件箱消息
     * @return 是否更新成功（0表示状态不符合条件，已被其他线程处理或已处理成功）
     */
    public boolean markProcessing(WebhookInboxDO inbox) {
        log.debug("[Webhook-Inbox] 尝试标记为处理中: messageId={}, eventType={}, 当前状态={}", 
            inbox.getMessageId(), inbox.getEventType(), inbox.getStatus());
        
        // 使用乐观锁：只有 PENDING 或 FAILED 状态才能更新为 PROCESSING
        // 这样可以避免重复处理已成功（SUCCESS）或正在处理（PROCESSING）的消息
        int updated = inboxRepository.markProcessing(inbox.getMessageId());
        
        if (updated > 0) {
            log.debug("[Webhook-Inbox] 已更新为处理中状态: messageId={}", inbox.getMessageId());
            inbox.setStatus(InboxStatusEnum.PROCESSING.name());  // 更新本地对象状态
            return true;
        } else {
            log.debug("[Webhook-Inbox] 状态更新失败，消息可能已被处理或正在处理: messageId={}, 当前状态={}", 
                inbox.getMessageId(), inbox.getStatus());
            return false;
        }
    }

    /**
     * 标记为成功（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 SUCCESS，避免重复标记
     * 
     * @param inbox 收件箱消息
     * @return 是否更新成功
     */
    public boolean markSuccess(WebhookInboxDO inbox) {
        log.debug("[Webhook-Inbox] 尝试标记为成功: messageId={}, eventType={}, processCount={}", 
            inbox.getMessageId(), inbox.getEventType(), inbox.getProcessCount());
        
        // 使用乐观锁：只有 PROCESSING 状态才能更新为 SUCCESS
        LocalDateTime processedTime = LocalDateTime.now();
        int updated = inboxRepository.markSuccess(inbox.getMessageId(), processedTime);
        
        if (updated > 0) {
            log.debug("[Webhook-Inbox] 已更新为成功状态: messageId={}, processedTime={}", 
                inbox.getMessageId(), processedTime);
            inbox.setStatus(InboxStatusEnum.SUCCESS.name());
            inbox.setProcessedTime(processedTime);
            return true;
        } else {
            log.warn("[Webhook-Inbox] 状态更新失败，消息可能已被其他线程处理: messageId={}, 当前状态={}", 
                inbox.getMessageId(), inbox.getStatus());
            return false;
        }
    }

    /**
     * 标记为失败（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 FAILED，避免重复标记
     * 
     * @param inbox 收件箱消息
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean markFailed(WebhookInboxDO inbox, String errorMessage) {
        int currentRetry = Objects.requireNonNullElse(inbox.getProcessCount(), 0);
        int newRetryCount = currentRetry + 1;
        LocalDateTime nextRetryTime = calculateNextRetryTime(newRetryCount, errorMessage);
        
        log.debug("[Webhook-Inbox] 尝试标记为失败: messageId={}, eventType={}, 当前重试次数={}, 新重试次数={}, nextRetryTime={}, error={}", 
            inbox.getMessageId(), inbox.getEventType(), currentRetry, newRetryCount, nextRetryTime, errorMessage);
        
        // 使用乐观锁：只有 PROCESSING 状态才能更新为 FAILED
        int updated = inboxRepository.markFailed(inbox.getMessageId(), newRetryCount, errorMessage, nextRetryTime);
        
        if (updated > 0) {
            log.debug("[Webhook-Inbox] 已更新为失败状态: messageId={}, processCount={}, nextRetryTime={}", 
                inbox.getMessageId(), newRetryCount, nextRetryTime);
            inbox.setProcessCount(newRetryCount);
            inbox.setStatus(InboxStatusEnum.FAILED.name());
            inbox.setLastError(errorMessage);
            inbox.setNextRetryTime(nextRetryTime);
            return true;
        } else {
            log.warn("[Webhook-Inbox] 状态更新失败，消息可能已被其他线程处理: messageId={}, 当前状态={}", 
                inbox.getMessageId(), inbox.getStatus());
            return false;
        }
    }

    public boolean reachMaxRetry(WebhookInboxDO inbox) {
        return Objects.requireNonNullElse(inbox.getProcessCount(), 0) >= maxRetryCount;
    }

    /**
     * 未匹配/不可重试的失败，直接封顶重试次数，避免无效重试（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 FAILED，避免重复标记
     * 
     * @param inbox 收件箱消息
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    public boolean markFailedNoRetry(WebhookInboxDO inbox, String errorMessage) {
        log.debug("[Webhook-Inbox] 尝试标记为失败（不可重试）: messageId={}, eventType={}, error={}", 
            inbox.getMessageId(), inbox.getEventType(), errorMessage);
        
        // 使用乐观锁：只有 PROCESSING 状态才能更新为 FAILED
        int updated = inboxRepository.markFailedNoRetry(inbox.getMessageId(), maxRetryCount, errorMessage);
        
        if (updated > 0) {
            log.debug("[Webhook-Inbox] 已更新为失败状态（不可重试）: messageId={}, processCount={}", 
                inbox.getMessageId(), maxRetryCount);
            inbox.setProcessCount(maxRetryCount);
            inbox.setStatus(InboxStatusEnum.FAILED.name());
            inbox.setLastError(errorMessage);
            inbox.setNextRetryTime(null);
            return true;
        } else {
            log.warn("[Webhook-Inbox] 状态更新失败，消息可能已被其他线程处理: messageId={}, 当前状态={}", 
                inbox.getMessageId(), inbox.getStatus());
            return false;
        }
    }

    /**
     * 计算下次重试时间
     * 
     * @param retryCount 重试次数
     * @param errorMessage 错误信息（用于判断是否为可快速重试的异常，如锁冲突）
     * @return 下次重试时间
     */
    private LocalDateTime calculateNextRetryTime(int retryCount, String errorMessage) {
        long delaySeconds;
        
        // 判断是否为可快速重试的异常（如锁冲突、并发处理等）
        // 这些异常通常很快就能恢复，使用更短的重试间隔
        if (errorMessage != null && (
            errorMessage.contains("正在处理中") || 
            errorMessage.contains("请稍后重试") ||
            errorMessage.contains("并发处理") ||
            errorMessage.contains("锁")
        )) {
            // 锁冲突等可快速重试的异常：使用固定短间隔（6秒，略大于锁超时时间5秒）
            delaySeconds = 6;
            log.debug("[Webhook-Inbox] 检测到可快速重试的异常，使用短重试间隔: {}秒", delaySeconds);
        } else {
            // 其他异常：使用指数退避策略
            delaySeconds = (long) (Math.pow(2, Math.max(0, retryCount - 1)) * retryIntervalBaseSeconds);
            // 上限保护：不超过 1 天
            delaySeconds = Math.min(delaySeconds, TimeUnit.DAYS.toSeconds(1));
        }
        
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
    
    /**
     * 清理已处理成功的消息（可选，用于定期清理，避免表数据过多）
     * 建议通过定时任务定期调用，例如：每天清理7天前已处理成功的消息
     * 
     * 实现策略：
     * - 批量删除，每次最多删除 batchSize 条，避免一次性删除过多数据
     * - 只删除 SUCCESS 状态的消息，确保不会误删未处理的消息
     * - 按处理时间排序，优先删除最早处理成功的消息
     * 
     * @param beforeDays 清理多少天前的数据（例如：7 表示清理7天前的数据）
     * @return 清理的记录数
     */
    public int cleanupSuccessMessages(int beforeDays) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(beforeDays);
        log.info("[Webhook-Inbox] 开始清理已处理成功的消息: 清理{}天前的数据（处理时间 < {}）", 
            beforeDays, cutoffTime);
        
        int totalDeleted = 0;
        int batchDeleted;
        
        // 批量删除，每次最多删除 batchSize 条，避免一次性删除过多数据
        do {
            // 查询要删除的消息ID（限制数量）
            List<WebhookInboxDO> toDelete = inboxRepository.findSuccessMessagesForCleanup(batchSize, cutoffTime);
            
            if (toDelete.isEmpty()) {
                break;
            }
            
            // 批量删除
            List<String> ids = toDelete.stream()
                .map(WebhookInboxDO::getId)
                .collect(Collectors.toList());
            
            batchDeleted = inboxRepository.deleteBatchByIds(ids);
            totalDeleted += batchDeleted;
            
            log.debug("[Webhook-Inbox] 批量删除: 本次删除{}条，累计删除{}条", batchDeleted, totalDeleted);
            
            // 如果本次删除数量小于查询数量，说明删除完成
            if (batchDeleted < toDelete.size()) {
                break;
            }
            
        } while (batchDeleted > 0);
        
        log.info("[Webhook-Inbox] 清理完成: 清理{}天前的数据，共删除{}条记录", beforeDays, totalDeleted);
        return totalDeleted;
    }
    
    /**
     * 统计收件箱消息数量（用于监控）
     * 
     * @return 各状态的消息数量统计
     */
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put(InboxStatusEnum.PENDING.name(), inboxRepository.countByStatus(InboxStatusEnum.PENDING.name()));
        stats.put(InboxStatusEnum.PROCESSING.name(), inboxRepository.countByStatus(InboxStatusEnum.PROCESSING.name()));
        stats.put(InboxStatusEnum.SUCCESS.name(), inboxRepository.countByStatus(InboxStatusEnum.SUCCESS.name()));
        stats.put(InboxStatusEnum.FAILED.name(), inboxRepository.countByStatus(InboxStatusEnum.FAILED.name()));
        return stats;
    }
}

