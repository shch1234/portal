package com.weili.iot_portal.task.webhook;

import com.weili.iot_portal.dal.repository.ingestion.WebhookMonitorRecordRepository;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.task.framework.BaseScheduledJob;
import com.weili.iot_portal.task.framework.JobExecutionResult;
import com.weili.iot_portal.task.webhook.config.WebhookCleanupConfig;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Webhook 统一清理任务
 * <p>
 * 功能：统一清理 webhook_inbox、webhook_fail_log、webhook_monitor_record 三个表的历史数据
 * </p>
 * <p>
 * 清理策略：
 * 1. webhook_inbox：清理已处理成功的消息（按 processed_time）
 * 2. webhook_fail_log：按恢复状态和人工处理标记分别清理（按 failed_time）
 *    - 已恢复的失败日志：保留 recoveredDays 天
 *    - 需要人工处理的失败日志：保留 manualDays 天
 *    - 其他失败日志：保留 otherDays 天
 * 3. webhook_monitor_record：清理监控记录（按 created_time）
 * </p>
 * <p>
 * 配置说明：
 * - 建议在 Apollo 配置 cron、路由策略、执行器等
 * - 建议在XXL-Job中配置cron表达式，例如：每天凌晨2点执行一次
 * - 清理天数可通过 Apollo 配置，详见 WebhookCleanupConfig
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookCleanupJob extends BaseScheduledJob {

    private final WebhookInboxService webhookInboxService;
    private final WebhookFailLogService webhookFailLogService;
    private final WebhookMonitorRecordRepository monitorRecordRepository;
    private final WebhookCleanupConfig cleanupConfig;

    @Override
    protected String getJobName() {
        return "Webhook统一清理任务";
    }

    @Override
    @XxlJob("webhookCleanupJob")
    public void execute() throws Exception {
        super.execute();
    }

    @Override
    protected JobExecutionResult executeInternal() throws Exception {
        int totalDeleted = 0;
        int inboxDeleted = 0;
        int failLogDeleted = 0;
        int monitorDeleted = 0;
        
        StringBuilder messageBuilder = new StringBuilder();
        
        try {
            // 1. 清理 webhook_inbox 表（已处理成功的消息）
            log.info("[Webhook-Cleanup] ====== 开始清理 webhook_inbox 表 ======");
            XxlJobHelper.log("开始清理 webhook_inbox 表，清理{}天前已处理成功的消息", 
                    cleanupConfig.getInbox().getSuccessDays());
            
            inboxDeleted = webhookInboxService.cleanupSuccessMessages(
                    cleanupConfig.getInbox().getSuccessDays());
            totalDeleted += inboxDeleted;
            
            String inboxMessage = String.format("webhook_inbox: 删除了%d条记录（清理%d天前已处理成功的消息）", 
                    inboxDeleted, cleanupConfig.getInbox().getSuccessDays());
            log.info("[Webhook-Cleanup] {}", inboxMessage);
            XxlJobHelper.log(inboxMessage);
            messageBuilder.append(inboxMessage).append("; ");
            
        } catch (Exception e) {
            log.error("[Webhook-Cleanup] 清理 webhook_inbox 表异常", e);
            XxlJobHelper.log("清理 webhook_inbox 表异常: {}", e.getMessage());
        }
        
        try {
            // 2. 清理 webhook_fail_log 表（按不同策略清理）
            log.info("[Webhook-Cleanup] ====== 开始清理 webhook_fail_log 表 ======");
            
            // 2.1 清理已恢复的失败日志
            int recoveredDeleted = webhookFailLogService.cleanup(
                    cleanupConfig.getFailLog().getRecoveredDays(), true, null);
            failLogDeleted += recoveredDeleted;
            String recoveredMessage = String.format("已恢复的失败日志: 删除了%d条记录（清理%d天前）", 
                    recoveredDeleted, cleanupConfig.getFailLog().getRecoveredDays());
            log.info("[Webhook-Cleanup] {}", recoveredMessage);
            XxlJobHelper.log(recoveredMessage);
            
            // 2.2 清理需要人工处理的失败日志
            int manualDeleted = webhookFailLogService.cleanup(
                    cleanupConfig.getFailLog().getManualDays(), null, true);
            failLogDeleted += manualDeleted;
            String manualMessage = String.format("需要人工处理的失败日志: 删除了%d条记录（清理%d天前）", 
                    manualDeleted, cleanupConfig.getFailLog().getManualDays());
            log.info("[Webhook-Cleanup] {}", manualMessage);
            XxlJobHelper.log(manualMessage);
            
            // 2.3 清理其他失败日志（未恢复且不需要人工处理）
            int otherDeleted = webhookFailLogService.cleanup(
                    cleanupConfig.getFailLog().getOtherDays(), false, false);
            failLogDeleted += otherDeleted;
            String otherMessage = String.format("其他失败日志: 删除了%d条记录（清理%d天前）", 
                    otherDeleted, cleanupConfig.getFailLog().getOtherDays());
            log.info("[Webhook-Cleanup] {}", otherMessage);
            XxlJobHelper.log(otherMessage);
            
            totalDeleted += failLogDeleted;
            String failLogMessage = String.format("webhook_fail_log: 共删除了%d条记录", failLogDeleted);
            log.info("[Webhook-Cleanup] {}", failLogMessage);
            XxlJobHelper.log(failLogMessage);
            messageBuilder.append(failLogMessage).append("; ");
            
        } catch (Exception e) {
            log.error("[Webhook-Cleanup] 清理 webhook_fail_log 表异常", e);
            XxlJobHelper.log("清理 webhook_fail_log 表异常: {}", e.getMessage());
        }
        
        try {
            // 3. 清理 webhook_monitor_record 表
            log.info("[Webhook-Cleanup] ====== 开始清理 webhook_monitor_record 表 ======");
            XxlJobHelper.log("开始清理 webhook_monitor_record 表，清理{}天前的监控记录", 
                    cleanupConfig.getMonitor().getRetentionDays());
            
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(
                    cleanupConfig.getMonitor().getRetentionDays());
            monitorDeleted = monitorRecordRepository.deleteBefore(cutoffTime);
            totalDeleted += monitorDeleted;
            
            String monitorMessage = String.format("webhook_monitor_record: 删除了%d条记录（清理%d天前）", 
                    monitorDeleted, cleanupConfig.getMonitor().getRetentionDays());
            log.info("[Webhook-Cleanup] {}", monitorMessage);
            XxlJobHelper.log(monitorMessage);
            messageBuilder.append(monitorMessage);
            
        } catch (Exception e) {
            log.error("[Webhook-Cleanup] 清理 webhook_monitor_record 表异常", e);
            XxlJobHelper.log("清理 webhook_monitor_record 表异常: {}", e.getMessage());
        }
        
        // 汇总结果
        String summaryMessage = String.format("清理完成，共删除%d条记录（inbox: %d, failLog: %d, monitor: %d）", 
                totalDeleted, inboxDeleted, failLogDeleted, monitorDeleted);
        log.info("[Webhook-Cleanup] ====== {}", summaryMessage);
        XxlJobHelper.log(summaryMessage);
        
        return JobExecutionResult.of(totalDeleted, 0, 0, messageBuilder.toString());
    }
}

