package com.weili.iot_portal.dal.repository.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 监控记录仓储
 */
public interface WebhookMonitorRecordRepository {

    /**
     * 插入单条记录
     */
    void insert(WebhookMonitorRecordDO record);

    /**
     * 批量插入记录
     */
    void insertBatch(List<WebhookMonitorRecordDO> records);

    /**
     * 删除指定时间之前的记录
     */
    int deleteBefore(LocalDateTime beforeTime);
}


