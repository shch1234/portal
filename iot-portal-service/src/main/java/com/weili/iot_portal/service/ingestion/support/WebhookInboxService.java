package com.weili.iot_portal.service.ingestion.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookInboxMapper;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebhookInboxService {

    @Autowired
    private WebhookInboxMapper inboxMapper;

    @Autowired
    private WebhookFailLogService webhookFailLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${webhook.inbox.batch-size:100}")
    private int batchSize;

    @Value("${webhook.inbox.max-retry-count:5}")
    private int maxRetryCount;

    @Value("${webhook.inbox.retry-interval-base-seconds:60}")
    private long retryIntervalBaseSeconds;

    public void saveToInbox(WebhookRequest request, DeviceBaseInfoDO device) {
        if (request == null || StringUtils.isBlank(request.getMessageId())) {
            throw new ServiceException(400, "缺少 messageId");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        payload.put("telemetryData", request.getTelemetryData());
        payload.put("metadata", request.getMetadata());
        payload.put("transactionInfo", request.getTransactionInfo());

        WebhookInboxDO inbox = new WebhookInboxDO();
        inbox.setMessageId(request.getMessageId());
        inbox.setTenantUuid(request.getTenantId());
        inbox.setTbDeviceId(request.getDeviceId());
        inbox.setDeviceCode(request.getDeviceCode());
        inbox.setEventType(request.getEventType());
        inbox.setWebhookCategory(request.getWebhookCategory());
        inbox.setPayload(payload);
        inbox.setStatus("PENDING");
        inbox.setProcessCount(0);
        inbox.setReceivedTime(LocalDateTime.now());
        inboxMapper.insert(inbox);
    }

    /**
     * 查询待处理/可重试的记录
     */
    public List<WebhookInboxDO> fetchDue() {
        LocalDateTime now = LocalDateTime.now();
        return inboxMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WebhookInboxDO>()
                        .in(WebhookInboxDO::getStatus, "PENDING", "FAILED")
                        .and(w -> w.isNull(WebhookInboxDO::getNextRetryTime).or().le(WebhookInboxDO::getNextRetryTime, now))
                        .orderByAsc(WebhookInboxDO::getReceivedTime)
                        .last("limit " + batchSize)
        );
    }

    public void markProcessing(WebhookInboxDO inbox) {
        inbox.setStatus("PROCESSING");
        inbox.setUpdateTime(LocalDateTime.now());
        inboxMapper.updateById(inbox);
    }

    public void markSuccess(WebhookInboxDO inbox) {
        inbox.setStatus("SUCCESS");
        inbox.setProcessedTime(LocalDateTime.now());
        inbox.setUpdateTime(LocalDateTime.now());
        inboxMapper.updateById(inbox);
    }

    public void markFailed(WebhookInboxDO inbox, String errorMessage) {
        int currentRetry = Objects.requireNonNullElse(inbox.getProcessCount(), 0);
        inbox.setProcessCount(currentRetry + 1);
        inbox.setStatus("FAILED");
        inbox.setLastError(errorMessage);
        inbox.setNextRetryTime(calculateNextRetryTime(currentRetry + 1));
        inbox.setUpdateTime(LocalDateTime.now());
        inboxMapper.updateById(inbox);
    }

    public boolean reachMaxRetry(WebhookInboxDO inbox) {
        return Objects.requireNonNullElse(inbox.getProcessCount(), 0) >= maxRetryCount;
    }

    /**
     * 未匹配/不可重试的失败，直接封顶重试次数，避免无效重试
     */
    public void markFailedNoRetry(WebhookInboxDO inbox, String errorMessage) {
        inbox.setProcessCount(maxRetryCount);
        inbox.setStatus("FAILED");
        inbox.setLastError(errorMessage);
        inbox.setNextRetryTime(null);
        inbox.setUpdateTime(LocalDateTime.now());
        inboxMapper.updateById(inbox);
    }

    private LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = (long) (Math.pow(2, Math.max(0, retryCount - 1)) * retryIntervalBaseSeconds);
        // 上限保护：不超过 1 天
        delaySeconds = Math.min(delaySeconds, TimeUnit.DAYS.toSeconds(1));
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
}

