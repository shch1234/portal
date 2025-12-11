package com.weili.iot_portal.dal.repository.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;

/**
 * Webhook 监控记录仓储
 */
public interface WebhookMonitorRecordRepository {

    void insert(WebhookMonitorRecordDO record);
}


