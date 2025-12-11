package com.weili.iot_portal.dal.repository.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 收件箱仓储
 */
public interface WebhookInboxRepository {

    void insert(WebhookInboxDO inbox);

    List<WebhookInboxDO> fetchDue(int limit, LocalDateTime now);

    void update(WebhookInboxDO inbox);

    /**
     * 使用乐观锁标记为处理中（用于异步处理）
     * 只有 PENDING 状态才能更新为 PROCESSING
     * 
     * @param messageId 消息ID
     * @return 更新的记录数（0表示状态不符合条件，已被其他线程处理）
     */
    int markProcessingWithLock(String messageId);

    /**
     * 根据 messageId 查询消息
     * 
     * @param messageId 消息ID
     * @return 消息对象，如果不存在返回 null
     */
    WebhookInboxDO findByMessageId(String messageId);

    /**
     * 查询待处理的新消息（PENDING状态）
     * 
     * @param limit 限制数量
     * @return 待处理的消息列表
     */
    List<WebhookInboxDO> fetchPendingMessages(int limit);

    /**
     * 查询失败重试的消息（FAILED状态）
     * 
     * @param limit 限制数量
     * @param now 当前时间
     * @return 失败重试的消息列表
     */
    List<WebhookInboxDO> fetchFailedMessages(int limit, LocalDateTime now);

    /**
     * 查询指定设备正在处理中的消息（用于诊断锁冲突）
     * 
     * @param deviceCode 设备编码
     * @param excludeMessageId 排除的消息ID（通常是当前消息本身）
     * @return 正在处理中的消息列表（不包含excludeMessageId）
     */
    List<WebhookInboxDO> findProcessingByDevice(String deviceCode, String excludeMessageId);

    /**
     * 查询指定设备的待处理或处理中的消息（用于诊断相同时间戳的并发问题）
     * 
     * @param deviceCode 设备编码
     * @param excludeMessageId 排除的消息ID（通常是当前消息本身）
     * @return 待处理或处理中的消息列表
     */
    List<WebhookInboxDO> findPendingOrProcessingByDevice(String deviceCode, String excludeMessageId);

    /**
     * 标记为处理中（使用乐观锁，确保幂等性）
     * 只有 PENDING 或 FAILED 状态才能更新为 PROCESSING
     * 
     * @param messageId 消息ID
     * @return 更新的记录数（0表示状态不符合条件）
     */
    int markProcessing(String messageId);

    /**
     * 标记为成功（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 SUCCESS
     * 
     * @param messageId 消息ID
     * @param processedTime 处理时间
     * @return 更新的记录数（0表示状态不符合条件）
     */
    int markSuccess(String messageId, LocalDateTime processedTime);

    /**
     * 标记为失败（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 FAILED
     * 
     * @param messageId 消息ID
     * @param processCount 重试次数
     * @param errorMessage 错误信息
     * @param nextRetryTime 下次重试时间
     * @return 更新的记录数（0表示状态不符合条件）
     */
    int markFailed(String messageId, int processCount, String errorMessage, LocalDateTime nextRetryTime);

    /**
     * 标记为失败（不可重试）（使用乐观锁，确保幂等性）
     * 只有 PROCESSING 状态才能更新为 FAILED
     * 
     * @param messageId 消息ID
     * @param maxRetryCount 最大重试次数
     * @param errorMessage 错误信息
     * @return 更新的记录数（0表示状态不符合条件）
     */
    int markFailedNoRetry(String messageId, int maxRetryCount, String errorMessage);

    /**
     * 查询已处理成功的消息（用于清理）
     * 
     * @param limit 限制数量
     * @param cutoffTime 截止时间
     * @return 已处理成功的消息列表
     */
    List<WebhookInboxDO> findSuccessMessagesForCleanup(int limit, LocalDateTime cutoffTime);

    /**
     * 批量删除消息
     * 
     * @param ids 消息ID列表
     * @return 删除的记录数
     */
    int deleteBatchByIds(List<String> ids);

    /**
     * 根据状态统计消息数量
     * 
     * @param status 状态
     * @return 消息数量
     */
    Long countByStatus(String status);
}



