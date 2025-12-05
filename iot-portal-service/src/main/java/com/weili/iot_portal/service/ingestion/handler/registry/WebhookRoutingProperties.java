package com.weili.iot_portal.service.ingestion.handler.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由配置（支持从 Apollo/配置中心注入）
 * 示例：
 * webhook:
 *   routing:
 *     alarm.*: alarmHandler
 *     biz.workpiece.start: workpieceStartHandler
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "webhook")
public class WebhookRoutingProperties {

    /**
     * pattern -> handler bean name
     */
    private Map<String, String> routing = new HashMap<>();
}

