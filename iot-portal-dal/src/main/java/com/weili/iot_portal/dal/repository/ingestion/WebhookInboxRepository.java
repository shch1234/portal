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
}



