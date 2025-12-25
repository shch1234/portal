package com.weili.iot_portal.service.ingestion.config;

import lombok.Data;

/**
 * Webhook Tomcat 配置
 * <p>
 * 所有配置项支持通过 Apollo 配置中心动态修改
 * Apollo 配置路径：webhook.server.tomcat.*
 */
@Data
public class WebhookTomcatConfig {
    /**
     * 最大线程数
     * Apollo 配置：webhook.server.tomcat.threads.max
     * 默认值：300（支持300+ QPS）
     */
    private Integer threadsMax = 300;

    /**
     * 最小空闲线程数
     * 默认值：50
     */
    private Integer threadsMinSpare = 50;

    /**
     * 最大连接数
     * 默认值：10000
     */
    private Integer maxConnections = 10000;

    /**
     * 接受队列大小
     * 默认值：1000
     */
    private Integer acceptCount = 1000;

    /**
     * 连接超时时间（毫秒）
     * 默认值：20000（20秒）
     */
    private Integer connectionTimeout = 20000;
}

