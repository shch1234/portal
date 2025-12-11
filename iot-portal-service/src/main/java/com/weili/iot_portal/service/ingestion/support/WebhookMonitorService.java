package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookMonitorRecordDO;
import com.weili.iot_portal.dal.repository.ingestion.WebhookMonitorRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Webhook 监控与告警埋点（可选，依赖 Micrometer）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookMonitorService {

    private final WebhookMonitorRecordRepository monitorRecordRepository;

    public void recordMatched(String eventType, String handlerName) {
        log.debug("Webhook matched handler: eventType={}, handler={}", eventType, handlerName);
        persist(eventType, handlerName, "MATCHED", null, null, null);
    }

    public void recordUnmatched(String eventType) {
        log.warn("Webhook unmatched handler: eventType={}", eventType);
        persist(eventType, null, "UNMATCHED", null, null, null);
    }

    public void recordSuccess(String eventType, long elapsedMs) {
        log.info("Webhook handled success: eventType={}, cost={}ms", eventType, elapsedMs);
        persist(eventType, null, "SUCCESS", elapsedMs, null, null);
    }

    public void recordFailure(String eventType, String error, long elapsedMs, boolean willRetry) {
        log.error("Webhook handled failure: eventType={}, cost={}ms, willRetry={}, error={}",
                eventType, elapsedMs, willRetry, error);
        persist(eventType, null, "FAILURE", elapsedMs, error, willRetry);
    }

    private String safe(String v) {
        return StringUtils.defaultIfBlank(v, "unknown");
    }

    /**
     * 将监控指标落库，便于离线统计/查询。
     */
    private void persist(String eventType, String handlerName, String status,
                         Long elapsedMs, String error, Boolean willRetry) {
        WebhookMonitorRecordDO record = new WebhookMonitorRecordDO();
        record.setEventType(safe(eventType));
        record.setHandlerName(StringUtils.isNotBlank(handlerName) ? handlerName : null);
        record.setStatus(status);
        record.setElapsedMs(elapsedMs);
        record.setErrorMessage(error);
        record.setWillRetry(willRetry);
        monitorRecordRepository.insert(record);
    }
}

