package com.weili.iot_portal.domain.devicebase;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Accessors(chain = true)
public class DeviceNetworkConfigVO {

    private String id;

    private String deviceId;

    private String deviceCode;

    private String deviceName;

    private String ipAddress;

    private Integer port;

    private String macAddress;

    private String gateway;

    private String subnetMask;

    private String protocol;

    private Map<String, Object> connectionParams;

    private String locationCode;

    private String locationDescription;

    private Map<String, Object> coordinates;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String updatedBy;
}

