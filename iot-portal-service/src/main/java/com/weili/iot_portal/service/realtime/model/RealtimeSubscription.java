package com.weili.iot_portal.service.realtime.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 实时推送订阅信息
 */
@Data
public class RealtimeSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订阅主题（用于区分不同的业务场景）
     * 例如：alarm.count:tenantId:factoryId:workshopId
     *      device.state:tenantId:factoryId:deviceId
     */
    private String topic;

    /**
     * 订阅参数（用于构建订阅键）
     */
    private Map<String, String> params;

    /**
     * 会话ID（WebSocket Session ID）
     */
    private String sessionId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 构建订阅键（用于分组管理连接）
     */
    public String getSubscriptionKey() {
        if (params == null || params.isEmpty()) {
            return topic + ":" + sessionId;
        }
        // 构建格式：topic:param1:param2:...
        StringBuilder key = new StringBuilder(topic);
        for (String value : params.values()) {
            if (value != null) {
                key.append(":").append(value);
            }
        }
        return key.toString();
    }

    /**
     * 从主题字符串构建订阅信息
     * 
     * @param topic 主题字符串，格式：topic:param1:param2:...
     */
    public static RealtimeSubscription fromTopic(String topic, String sessionId) {
        RealtimeSubscription subscription = new RealtimeSubscription();
        subscription.setTopic(topic);
        subscription.setSessionId(sessionId);
        return subscription;
    }
}

