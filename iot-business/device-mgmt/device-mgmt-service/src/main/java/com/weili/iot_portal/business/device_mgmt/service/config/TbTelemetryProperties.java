package com.weili.iot_portal.business.device_mgmt.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ThingsBoard Telemetry 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "thingsboard.api")
public class TbTelemetryProperties {

    /**
     * TB API 基础地址，例如 http://thingsboard:8080
     */
    private String url;

    /**
     * JWT Token 或固定访问令牌
     */
    private String token;
}


