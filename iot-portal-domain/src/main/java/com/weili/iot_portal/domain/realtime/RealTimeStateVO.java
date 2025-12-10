package com.weili.iot_portal.domain.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealTimeStateVO {
    private String factoryId;
    private String deviceId;

    private String state;
    private String stateCode;
    private Long updatedAt;
    private String source;
    private String traceId;
    private boolean stale;

    /** 额外字段（如自定义状态属性） */
    private Map<String, String> extra;
}

