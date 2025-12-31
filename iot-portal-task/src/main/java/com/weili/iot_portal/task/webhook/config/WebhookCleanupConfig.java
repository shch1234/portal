package com.weili.iot_portal.task.webhook.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Webhook 统一清理任务配置
 * <p>
 * Apollo 配置前缀：webhook.cleanup.*
 * </p>
 * <p>
 * 配置示例：
 * webhook.cleanup.inbox.success-days=3        # 清理多少天前已处理成功的收件箱消息
 * webhook.cleanup.fail-log.recovered-days=7   # 清理多少天前已恢复的失败日志
 * webhook.cleanup.fail-log.manual-days=30     # 清理多少天前需要人工处理的失败日志
 * webhook.cleanup.fail-log.other-days=7       # 清理多少天前其他失败日志
 * webhook.cleanup.monitor.retention-days=30   # 清理多少天前的监控记录
 * </p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "webhook.cleanup")
public class WebhookCleanupConfig {

    /**
     * 收件箱清理配置
     */
    private InboxConfig inbox = new InboxConfig();

    /**
     * 失败日志清理配置
     */
    private FailLogConfig failLog = new FailLogConfig();

    /**
     * 监控记录清理配置
     */
    private MonitorConfig monitor = new MonitorConfig();

    @Data
    public static class InboxConfig {
        /**
         * 清理多少天前已处理成功的消息
         * Apollo 配置：webhook.cleanup.inbox.success-days
         */
        @Min(1)
        private Integer successDays = 3;
    }

    @Data
    public static class FailLogConfig {
        /**
         * 清理多少天前已恢复的失败日志
         * Apollo 配置：webhook.cleanup.fail-log.recovered-days
         */
        @Min(1)
        private Integer recoveredDays = 3;

        /**
         * 清理多少天前需要人工处理的失败日志
         * Apollo 配置：webhook.cleanup.fail-log.manual-days
         */
        @Min(1)
        private Integer manualDays = 3;

        /**
         * 清理多少天前其他失败日志
         * Apollo 配置：webhook.cleanup.fail-log.other-days
         */
        @Min(1)
        private Integer otherDays = 3;
    }

    @Data
    public static class MonitorConfig {
        /**
         * 清理多少天前的监控记录
         * Apollo 配置：webhook.cleanup.monitor.retention-days
         */
        @Min(1)
        private Integer retentionDays = 3;
    }
}

