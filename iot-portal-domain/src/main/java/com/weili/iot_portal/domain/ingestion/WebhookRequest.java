package com.weili.iot_portal.domain.ingestion;

import lombok.Data;

import java.util.Map;

/**
 * Webhook 请求 DTO（明文或解密后）
 */
@Data
public class WebhookRequest {

    private String messageId;

    private String tenantId;

    private String deviceId;

    private String deviceCode;

    private String eventType;

    private Long timestamp;

    private Long dataTimestamp;

    /**
     * BUSINESS | REALTIME
     */
    private String webhookCategory;

    private Map<String, Object> eventData;

    private Map<String, Object> telemetryData;

    private Map<String, Object> metadata;

    private Map<String, Object> transactionInfo;
}

