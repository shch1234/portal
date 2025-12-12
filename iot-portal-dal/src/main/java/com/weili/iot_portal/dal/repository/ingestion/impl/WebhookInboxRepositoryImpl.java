package com.weili.iot_portal.dal.repository.ingestion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.common.enums.InboxStatusEnum;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookInboxMapper;
import com.weili.iot_portal.dal.repository.ingestion.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class WebhookInboxRepositoryImpl implements WebhookInboxRepository {

    private final WebhookInboxMapper inboxMapper;

    @Override
    public void insert(WebhookInboxDO inbox) {
        inboxMapper.insert(inbox);
    }

    @Override
    public List<WebhookInboxDO> fetchDue(int limit, LocalDateTime now) {
        return inboxMapper.selectList(
                new LambdaQueryWrapper<WebhookInboxDO>()
                        .in(WebhookInboxDO::getStatus, InboxStatusEnum.PENDING.name(), InboxStatusEnum.FAILED.name())
                        .and(w -> w.isNull(WebhookInboxDO::getNextRetryTime)
                                .or()
                                .le(WebhookInboxDO::getNextRetryTime, now))
                        .orderByAsc(WebhookInboxDO::getReceivedTime)
                        .last("limit " + limit)
        );
    }

    @Override
    public void update(WebhookInboxDO inbox) {
        inboxMapper.updateById(inbox);
    }

    @Override
    public int markProcessingWithLock(String messageId) {
        return inboxMapper.update(null,
            new LambdaUpdateWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PENDING.name())
                .set(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
        );
    }

    @Override
    public WebhookInboxDO findByMessageId(String messageId) {
        return inboxMapper.selectOne(
            new LambdaQueryWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
        );
    }

    @Override
    public List<WebhookInboxDO> fetchPendingMessages(int limit) {
        return inboxMapper.selectList(
            new LambdaQueryWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PENDING.name())
                .orderByAsc(WebhookInboxDO::getReceivedTime)
                .last("limit " + limit)
        );
    }

    @Override
    public List<WebhookInboxDO> fetchFailedMessages(int limit, LocalDateTime now) {
        return inboxMapper.selectList(
            new LambdaQueryWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.FAILED.name())
                .le(WebhookInboxDO::getNextRetryTime, now)
                .orderByAsc(WebhookInboxDO::getNextRetryTime)
                .last("limit " + limit)
        );
    }

    @Override
    public List<WebhookInboxDO> findProcessingByDevice(String deviceCode, String excludeMessageId) {
        LambdaQueryWrapper<WebhookInboxDO> wrapper = new LambdaQueryWrapper<WebhookInboxDO>()
            .eq(WebhookInboxDO::getDeviceCode, deviceCode)
            .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
            .orderByAsc(WebhookInboxDO::getReceivedTime);
        
        if (StringUtils.isNotBlank(excludeMessageId)) {
            wrapper.ne(WebhookInboxDO::getMessageId, excludeMessageId);
        }
        
        return inboxMapper.selectList(wrapper);
    }

    @Override
    public List<WebhookInboxDO> findPendingOrProcessingByDevice(String deviceCode, String excludeMessageId) {
        LambdaQueryWrapper<WebhookInboxDO> wrapper = new LambdaQueryWrapper<WebhookInboxDO>()
            .eq(WebhookInboxDO::getDeviceCode, deviceCode)
            .in(WebhookInboxDO::getStatus, Arrays.asList(InboxStatusEnum.PENDING.name(), InboxStatusEnum.PROCESSING.name()))
            .orderByAsc(WebhookInboxDO::getReceivedTime);
        
        if (StringUtils.isNotBlank(excludeMessageId)) {
            wrapper.ne(WebhookInboxDO::getMessageId, excludeMessageId);
        }
        
        return inboxMapper.selectList(wrapper);
    }

    @Override
    public int markProcessing(String messageId) {
        return inboxMapper.update(null,
            new LambdaUpdateWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
                .in(WebhookInboxDO::getStatus, InboxStatusEnum.PENDING.name(), InboxStatusEnum.FAILED.name())
                .set(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
        );
    }

    @Override
    public int markSuccess(String messageId, LocalDateTime processedTime) {
        return inboxMapper.update(null,
            new LambdaUpdateWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
                .set(WebhookInboxDO::getStatus, InboxStatusEnum.SUCCESS.name())
                .set(WebhookInboxDO::getProcessedTime, processedTime)
        );
    }

    @Override
    public int markFailed(String messageId, int processCount, String errorMessage, LocalDateTime nextRetryTime) {
        return inboxMapper.update(null,
            new LambdaUpdateWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
                .set(WebhookInboxDO::getProcessCount, processCount)
                .set(WebhookInboxDO::getStatus, InboxStatusEnum.FAILED.name())
                .set(WebhookInboxDO::getLastError, errorMessage)
                .set(WebhookInboxDO::getNextRetryTime, nextRetryTime)
        );
    }

    @Override
    public int markFailedNoRetry(String messageId, int maxRetryCount, String errorMessage) {
        return inboxMapper.update(null,
            new LambdaUpdateWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getMessageId, messageId)
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.PROCESSING.name())
                .set(WebhookInboxDO::getProcessCount, maxRetryCount)
                .set(WebhookInboxDO::getStatus, InboxStatusEnum.FAILED.name())
                .set(WebhookInboxDO::getLastError, errorMessage)
                .set(WebhookInboxDO::getNextRetryTime, null)
        );
    }

    @Override
    public List<WebhookInboxDO> findSuccessMessagesForCleanup(int limit, LocalDateTime cutoffTime) {
        return inboxMapper.selectList(
            new LambdaQueryWrapper<WebhookInboxDO>()
                .select(WebhookInboxDO::getId)
                .eq(WebhookInboxDO::getStatus, InboxStatusEnum.SUCCESS.name())
                .lt(WebhookInboxDO::getProcessedTime, cutoffTime)
                .orderByAsc(WebhookInboxDO::getProcessedTime)
                .last("limit " + limit)
        );
    }

    @Override
    public int deleteBatchByIds(List<String> ids) {
        return inboxMapper.deleteByIds(ids);
    }

    @Override
    public Long countByStatus(String status) {
        return inboxMapper.selectCount(
            new LambdaQueryWrapper<WebhookInboxDO>()
                .eq(WebhookInboxDO::getStatus, status)
        );
    }
}






