package com.weili.iot_portal.task.webhook;

import com.weili.iot_portal.service.ingestion.WebhookInboxProcessor;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook 收件箱处理任务（XXL-Job）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookInboxJob extends BaseScheduledJob {

    private final WebhookInboxProcessor webhookInboxProcessor;

    @Override
    protected String getJobName() {
        return "Webhook收件箱处理任务";
    }

    @Override
    @XxlJob("webhookInboxJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        WebhookInboxProcessor.ProcessResult result = webhookInboxProcessor.processBatch();

        XxlJobHelper.log("Webhook 收件箱处理完成: 成功={}, 跳过={}, 失败={}",
                result.successCount(), result.skipCount(), result.errorCount());

        return JobExecutionResult.of(
                result.successCount(),
                result.skipCount(),
                result.errorCount());
    }
}

