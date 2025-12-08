package com.weili.iot_portal.domain.devicebase.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class DeviceNetworkConfigCreateReq {

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

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
}

