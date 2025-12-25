package com.weili.iot_portal.service.ingestion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Webhook 服务器配置
 * <p>
 * 所有配置项支持通过 Apollo 配置中心动态修改
 * Apollo 配置路径：webhook.server.*
 *
 * @author System
 */
@Data
@Component
@ConfigurationProperties(prefix = "webhook.server")
public class WebhookServerConfig {

    /**
     * Tomcat 配置
     */
    private WebhookTomcatConfig tomcat = new WebhookTomcatConfig();

    /**
     * 异步处理配置
     */
    private WebhookAsyncConfig async = new WebhookAsyncConfig();
}

