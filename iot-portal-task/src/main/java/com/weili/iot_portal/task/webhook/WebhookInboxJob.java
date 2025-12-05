package com.weili.iot_portal.task.webhook;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.service.ingestion.WebhookProcessWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Webhook 收件箱处理任务（XXL-Job）
 * 建议在 Apollo 配置 cron、路由策略、执行器等
 */
@Slf4j
@Component
public class WebhookInboxJob {

    @Autowired
    private WebhookProcessWorker webhookProcessWorker;

    /**
     * 执行入口
     */
    @XxlJob("webhookInboxJob")
    public void execute() {
        int success = webhookProcessWorker.processBatch();
        XxlJobHelper.log("webhook inbox processed: {}", success);
    }
}

