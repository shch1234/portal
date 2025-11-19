package com.weili.iot_portal.business.device_base.domain.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 更新设备配置请求
 */
@Data
public class DeviceConfigurationUpdateReq {

    @NotBlank(message = "配置ID不能为空")
    private String id;

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

