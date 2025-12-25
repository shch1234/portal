package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.service.model.ProcessResult;

/**
 * Webhook 收件箱批处理服务
 * 供定时任务等入口调用
 */
public interface WebhookInboxProcessor {

    /**
     * 批量处理待处理的收件箱消息
     *
     * @return 处理结果（成功 / 跳过 / 失败数量）
     */
    ProcessResult processBatch();
}

