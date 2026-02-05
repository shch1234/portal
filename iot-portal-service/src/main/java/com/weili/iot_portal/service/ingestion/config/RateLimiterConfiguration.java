package com.weili.iot_portal.service.ingestion.config;

import com.weili.iot_portal.service.ingestion.support.AdaptiveRateLimiter;
import com.weili.iot_portal.service.ingestion.support.WebhookRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 限流器配置类
 * <p>
 * 用于连接 WebhookRateLimiter 和 AdaptiveRateLimiter，避免循环依赖
 * </p>
 *
 * @author system
 */
@Slf4j
@Configuration
public class RateLimiterConfiguration {

    @Autowired(required = false)
    private WebhookRateLimiter webhookRateLimiter;

    @Autowired(required = false)
    private AdaptiveRateLimiter adaptiveRateLimiter;

    @PostConstruct
    public void configure() {
        if (webhookRateLimiter != null && adaptiveRateLimiter != null) {
            webhookRateLimiter.setAdaptiveRateLimiter(adaptiveRateLimiter);
            log.info("[RateLimiterConfig] 自适应限流已连接到基础限流器");
        } else {
            if (webhookRateLimiter == null) {
                log.warn("[RateLimiterConfig] WebhookRateLimiter 未找到，自适应限流未启用");
            }
            if (adaptiveRateLimiter == null) {
                log.info("[RateLimiterConfig] AdaptiveRateLimiter 未找到，使用固定限流");
            }
        }
    }
}
