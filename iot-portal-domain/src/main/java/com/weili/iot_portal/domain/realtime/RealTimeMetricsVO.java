package com.weili.iot_portal.domain.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealTimeMetricsVO {
    private String factoryId;
    private String deviceId;

    /** metric.xxx -> value */
    private Map<String, String> metrics;
    private Long updatedAt;
    private String source;
    private String traceId;
    private boolean stale;
}

