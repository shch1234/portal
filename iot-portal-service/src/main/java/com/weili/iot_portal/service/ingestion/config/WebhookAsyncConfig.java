package com.weili.iot_portal.service.ingestion.config;

import lombok.Data;

/**
 * Webhook 异步处理配置
 * <p>
 * 所有配置项支持通过 Apollo 配置中心动态修改
 * Apollo 配置路径：webhook.server.async.*
 */
@Data
public class WebhookAsyncConfig {
    /**
     * 核心线程数
     * Apollo 配置：webhook.server.async.core-size
     * 默认值：50
     */
    private Integer coreSize = 50;

    /**
     * 最大线程数
     * Apollo 配置：webhook.server.async.max-size
     * 默认值：200
     */
    private Integer maxSize = 200;

    /**
     * 队列容量
     * Apollo 配置：webhook.server.async.queue-capacity
     * 默认值：1000
     */
    private Integer queueCapacity = 1000;

    /**
     * 线程名前缀
     */
    private String threadNamePrefix = "webhook-async-";
}

