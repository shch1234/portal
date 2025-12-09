package com.weili.iot_portal.domain.devicemng;

import lombok.Data;

@Data
public class DeviceAlarmVO {
    private String id;
    private String tenantId;
    private String factoryId;
    private String deviceId;
    private String alarmCode;
    private String alarmText;
    private String alarmLevel;
    private Long startTs;
    private Long endTs;
    private Integer durationS;
    private String properties;
    private Integer isActive;
}


