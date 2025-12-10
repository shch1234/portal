package com.weili.iot_portal.service.ingestion.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 监控与告警埋点（可选，依赖 Micrometer）
 */
@Slf4j
@Component
public class WebhookMonitorService {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public void recordMatched(String eventType, String handlerName) {
        log.debug("Webhook matched handler: eventType={}, handler={}", eventType, handlerName);
        if (meterRegistry != null) {
            Counter.builder("webhook.handler.matched")
                    .tag("eventType", safe(eventType))
                    .tag("handler", safe(handlerName))
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordUnmatched(String eventType) {
        log.warn("Webhook unmatched handler: eventType={}", eventType);
        if (meterRegistry != null) {
            Counter.builder("webhook.handler.unmatched")
                    .tag("eventType", safe(eventType))
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordSuccess(String eventType, long elapsedMs) {
        log.info("Webhook handled success: eventType={}, cost={}ms", eventType, elapsedMs);
        if (meterRegistry != null) {
            Timer.builder("webhook.handler.success")
                    .tag("eventType", safe(eventType))
                    .register(meterRegistry)
                    .record(elapsedMs, TimeUnit.MILLISECONDS);
        }
    }

    public void recordFailure(String eventType, String error, long elapsedMs, boolean willRetry) {
        log.error("Webhook handled failure: eventType={}, cost={}ms, willRetry={}, error={}",
                eventType, elapsedMs, willRetry, error);
        if (meterRegistry != null) {
            Timer.builder("webhook.handler.failure")
                    .tag("eventType", safe(eventType))
                    .tag("retry", String.valueOf(willRetry))
                    .register(meterRegistry)
                    .record(elapsedMs, TimeUnit.MILLISECONDS);
        }
    }

    private String safe(String v) {
        return StringUtils.defaultIfBlank(v, "unknown");
    }
}

