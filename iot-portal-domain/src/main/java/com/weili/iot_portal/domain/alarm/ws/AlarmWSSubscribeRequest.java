package com.weili.iot_portal.domain.alarm.ws;

import lombok.Data;

import java.io.Serializable;

/**
 * WebSocket订阅请求模型
 */
@Data
public class AlarmWSSubscribeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工厂ID
     */
    private String factoryId;

    /**
     * 车间ID（可选，null表示订阅整个工厂）
     */
    private String workshopId;

    /**
     * 构建订阅键（用于分组管理连接）
     */
    public String getSubscriptionKey() {
        if (workshopId != null && !workshopId.isEmpty()) {
            return factoryId + ":" + workshopId;
        } else {
            return factoryId;
        }
    }
}

