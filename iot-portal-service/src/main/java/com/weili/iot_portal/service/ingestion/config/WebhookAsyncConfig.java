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
     * 默认值：2000（已从1000增加到2000，提供更多缓冲）
     */
    private Integer queueCapacity = 10000;

    /**
     * 线程保活时间（秒）
     * Apollo 配置：webhook.server.async.keep-alive-seconds
     * 默认值：60
     * 当线程空闲超过此时间时会被回收（仅当allowCoreThreadTimeOut=true时，核心线程也会被回收）
     */
    private Integer keepAliveSeconds = 60;

    /**
     * 是否允许核心线程超时
     * Apollo 配置：webhook.server.async.allow-core-thread-timeout
     * 默认值：false
     * 如果为true，核心线程在空闲超过keepAliveSeconds时也会被回收
     */
    private Boolean allowCoreThreadTimeOut = false;

    /**
     * 线程名前缀
     */
    private String threadNamePrefix = "webhook-async-";
}

