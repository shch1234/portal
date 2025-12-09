package com.weili.iot_portal.domain.realtime;

import lombok.Data;

import java.util.Map;

@Data
public class RealTimeProgramVO {
    private String tenantId;
    private String factoryId;
    private String deviceId;
    private String programName;
    private String programPath;
    private String gCode;
    private String mCode;
    private Long updatedAt;
    private boolean stale;
    private Map<String, String> extra;
}


