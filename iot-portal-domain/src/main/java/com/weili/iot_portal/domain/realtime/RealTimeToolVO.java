package com.weili.iot_portal.domain.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealTimeToolVO {
    private String factoryId;
    private String deviceId;

    /** 刀具号、刀套号、刀补等字段直接透出 */
    private Map<String, String> toolData;
    private Long updatedAt;
    private String source;
    private String traceId;
    private boolean stale;
}

