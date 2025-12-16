package com.weili.iot_portal.task.webhook;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.weili.iot_portal.task.webhook.config.WebhookInboxCleanupConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Webhook 收件箱清理任务
 * 
 * 功能：定期清理已处理成功的收件箱消息，避免表数据过多
 * 
 * 配置说明：
 * - 建议在 Apollo 配置 cron、路由策略、执行器等
 * - 建议在XXL-Job中配置cron表达式，例如：每天凌晨2点执行一次
 * - 清理天数可通过 Apollo 配置：webhook.inbox.cleanup.success-days（默认3天）
 * 
 * Apollo 配置示例：
 * webhook.inbox.cleanup.success-days=3  # 清理3天前已处理成功的消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookInboxCleanupJob extends BaseScheduledJob {

    private final WebhookInboxService webhookInboxService;
    private final WebhookInboxCleanupConfig cleanupConfig;

    @Override
    protected String getJobName() {
        return "Webhook收件箱清理任务";
    }

    @Override
    @XxlJob("webhookInboxCleanupJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        int cleanupDays = cleanupConfig.getSuccessDays();
        XxlJobHelper.log("开始清理收件箱，清理{}天前已处理成功的消息", cleanupDays);
        log.info("[Webhook-Inbox-Cleanup] 开始清理收件箱，清理{}天前已处理成功的消息", cleanupDays);
        
        // 调用清理方法
        int deletedCount = webhookInboxService.cleanupSuccessMessages(cleanupDays);
        
        String message = String.format("清理完成，删除了%d条记录（清理%d天前已处理成功的消息）", deletedCount, cleanupDays);
        XxlJobHelper.log(message);
        log.info("[Webhook-Inbox-Cleanup] {}", message);
        
        // 返回执行结果
        return JobExecutionResult.of(deletedCount, 0, 0, message);
    }
}

