package com.weili.iot_portal.task.webhook;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.service.ingestion.WebhookProcessWorker;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook 收件箱处理任务（XXL-Job）
 * 
 * 功能：定时处理 Webhook 收件箱中的待处理消息
 * 
 * 配置说明：
 * - 建议在 Apollo 配置 cron、路由策略、执行器等
 * - 建议在XXL-Job中配置cron表达式，例如：每1分钟执行一次
 * 
 * 使用框架：BaseScheduledJob
 * - 自动异常处理
 * - 自动统计收集
 * - 自动日志记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookInboxJob extends BaseScheduledJob {

    private final WebhookProcessWorker webhookProcessWorker;

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
        // 调用 Worker 处理批次
        // 注意：processBatch() 只返回成功数量，失败和跳过数量在 Worker 内部处理
        int successCount = webhookProcessWorker.processBatch();
        
        // 由于 Worker 内部已经处理了异常和统计，这里只返回成功数量
        // 失败和跳过的消息会在 Worker 内部标记，并在下次重试
        return JobExecutionResult.of(successCount, 0, 0);
    }
}

