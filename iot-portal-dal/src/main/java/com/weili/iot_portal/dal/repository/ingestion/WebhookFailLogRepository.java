package com.weili.iot_portal.dal.repository.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;

import java.util.List;
import java.util.Optional;

/**
 * Webhook 失败日志仓储
 */
public interface WebhookFailLogRepository {

    void insert(WebhookFailLogDO failLog);

    List<WebhookFailLogDO> findUnrecovered(int limit);

    Optional<WebhookFailLogDO> findById(String id);

    void update(WebhookFailLogDO failLog);

    Optional<WebhookFailLogDO> findLatestByMessageId(String messageId);
}



