package com.weili.iot_portal.service.ingestion.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Webhook 服务器配置类
 * <p>
 * 配置 Tomcat 线程池和异步处理线程池
 * </p>
 * <p>
 * 注意：Tomcat 配置使用 Spring Boot 标准属性（server.tomcat.*），
 * 这些属性可以通过 Apollo 动态修改
 * </p>
 *
 * @author System
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
@EnableConfigurationProperties(WebhookServerConfig.class)
@ConditionalOnWebApplication
public class WebhookServerConfiguration {

    private final WebhookServerConfig webhookServerConfig;

    /**
     * 配置 Tomcat 线程池
     * <p>
     * 使用 Spring Boot 标准配置（server.tomcat.*），
     * 如果需要在代码中动态配置，可以使用此方法
     * </p>
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webhookTomcatCustomizer() {
        return factory -> {
            WebhookTomcatConfig tomcat = webhookServerConfig.getTomcat();
            
            // 配置连接器
            factory.addConnectorCustomizers(connector -> {
                // 设置最大连接数
                connector.setProperty("maxConnections", String.valueOf(tomcat.getMaxConnections()));
                // 设置接受队列大小
                connector.setProperty("acceptCount", String.valueOf(tomcat.getAcceptCount()));
                // 设置连接超时时间（毫秒）
                connector.setProperty("connectionTimeout", String.valueOf(tomcat.getConnectionTimeout()));
            });

            // 配置线程池
            factory.addContextCustomizers(context -> {
                // 设置最大线程数
                context.addParameter("maxThreads", String.valueOf(tomcat.getThreadsMax()));
                // 设置最小空闲线程数
                context.addParameter("minSpareThreads", String.valueOf(tomcat.getThreadsMinSpare()));
            });

            log.info("[Webhook-Server] Tomcat配置完成: maxThreads={}, minSpareThreads={}, maxConnections={}, acceptCount={}, connectionTimeout={}ms",
                    tomcat.getThreadsMax(), tomcat.getThreadsMinSpare(), tomcat.getMaxConnections(),
                    tomcat.getAcceptCount(), tomcat.getConnectionTimeout());
        };
    }

    /**
     * 配置异步处理线程池
     * <p>
     * 注意：配置了 TaskDecorator 以自动传递 MDC 上下文，确保日志中能正确显示设备编号
     * </p>
     */
    @Bean(name = "webhookAsyncExecutor")
    public Executor webhookAsyncExecutor() {
        WebhookAsyncConfig async = webhookServerConfig.getAsync();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCoreSize());
        executor.setMaxPoolSize(async.getMaxSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix(async.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 配置 TaskDecorator 以自动传递 MDC 上下文（用于 @Async 方法）
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();

        log.info("[Webhook-Server] 异步线程池配置完成: coreSize={}, maxSize={}, queueCapacity={}, threadNamePrefix={}",
                async.getCoreSize(), async.getMaxSize(), async.getQueueCapacity(), async.getThreadNamePrefix());

        return executor;
    }
    
    /**
     * MDC 任务装饰器
     * <p>
     * 用于在异步任务执行时自动传递 MDC 上下文，确保日志中能正确显示设备编号等信息
     * </p>
     */
    private static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 获取当前线程的 MDC 上下文
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    // 在异步线程中恢复 MDC 上下文
                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }
                    // 执行原始任务
                    runnable.run();
                } finally {
                    // 清除 MDC，避免线程复用导致上下文污染
                    MDC.clear();
                }
            };
        }
    }
}

