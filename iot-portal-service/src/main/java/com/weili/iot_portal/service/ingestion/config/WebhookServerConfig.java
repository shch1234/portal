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
    private TomcatConfig tomcat = new TomcatConfig();

    /**
     * 异步处理配置
     */
    private AsyncConfig async = new AsyncConfig();

    /**
     * Tomcat 配置
     */
    @Data
    public static class TomcatConfig {
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

    /**
     * 异步处理配置
     */
    @Data
    public static class AsyncConfig {
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
}

