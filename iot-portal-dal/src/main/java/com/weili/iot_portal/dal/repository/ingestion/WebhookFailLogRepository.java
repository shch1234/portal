package com.weili.iot_portal.dal.repository.ingestion;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookFailLogDO;

import java.time.LocalDateTime;
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

    /**
     * 删除指定时间之前的记录
     * 
     * @param beforeTime 截止时间
     * @param recovered 是否已恢复（null 表示不限制）
     * @param needManual 是否需要人工处理（null 表示不限制）
     * @return 删除的记录数
     */
    int deleteBefore(LocalDateTime beforeTime, Boolean recovered, Boolean needManual);
}



