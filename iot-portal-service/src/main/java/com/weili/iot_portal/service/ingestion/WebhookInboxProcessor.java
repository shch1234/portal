package com.weili.iot_portal.service.ingestion;

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

    /**
     * 处理结果
     */
    record ProcessResult(int successCount, int skipCount, int errorCount) {
        public static ProcessResult empty() {
            return new ProcessResult(0, 0, 0);
        }
    }
}

