package com.weili.iot_portal.domain.realtime;

import lombok.Data;

@Data
public class RealTimeOnlineVO {
    private String tenantId;
    private String factoryId;
    private String deviceId;

    private boolean online;
    private Long ttlSeconds;
}

