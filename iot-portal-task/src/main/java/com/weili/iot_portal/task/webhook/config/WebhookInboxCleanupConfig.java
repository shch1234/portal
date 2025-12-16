package com.weili.iot_portal.task.webhook.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Webhook 收件箱清理任务配置
 *
 * Apollo 配置前缀：webhook.inbox.cleanup.*
 *
 * 建议配置示例：
 * - webhook.inbox.cleanup.success-days=3   # 清理多少天前已处理成功的消息
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "webhook.inbox.cleanup")
public class WebhookInboxCleanupConfig {

    /**
     * 清理多少天前已处理成功的消息
     * Apollo 配置：webhook.inbox.cleanup.success-days
     */
    @NotNull(message = "webhook.inbox.cleanup.success-days 未配置，将使用默认值 3")
    @Min(1)
    private Integer successDays = 3;
}


