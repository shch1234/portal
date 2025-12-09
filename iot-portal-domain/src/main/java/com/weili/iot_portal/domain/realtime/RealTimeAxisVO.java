package com.weili.iot_portal.domain.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealTimeAxisVO {
    private String tenantId;
    private String factoryId;
    private String deviceId;

    /** axis.{axisName}.xxx -> value */
    private Map<String, String> axes;
    private Long updatedAt;
    private String source;
    private String traceId;
    private boolean stale;
}

