package com.weili.iot_portal.service.realtime.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 实时推送消息模型（通用）
 */
@Data
public class RealtimeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息主题（用于区分不同的业务场景）
     * 例如：alarm.count, device.state, device.metrics等
     */
    private String topic;

    /**
     * 消息负载（根据type和topic不同，内容不同）
     */
    private Object payload;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 消息ID（用于去重和追踪）
     */
    private String messageId;

    public RealtimeMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public RealtimeMessage(String type, String topic, Object payload) {
        this.type = type;
        this.topic = topic;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 创建消息
     */
    public static RealtimeMessage of(String type, String topic, Object payload) {
        return new RealtimeMessage(type, topic, payload);
    }

    /**
     * 创建订阅消息
     */
    public static RealtimeMessage subscribe(String topic) {
        return new RealtimeMessage("SUBSCRIBE", topic, null);
    }

    /**
     * 创建取消订阅消息
     */
    public static RealtimeMessage unsubscribe(String topic) {
        return new RealtimeMessage("UNSUBSCRIBE", topic, null);
    }

    /**
     * 创建订阅确认消息
     */
    public static RealtimeMessage subscribed(String topic) {
        return new RealtimeMessage("SUBSCRIBED", topic, "订阅成功");
    }

    /**
     * 创建数据更新消息
     */
    public static RealtimeMessage dataChanged(String topic, Object data) {
        return new RealtimeMessage("DATA_CHANGED", topic, data);
    }

    /**
     * 创建心跳响应消息
     */
    public static RealtimeMessage pong() {
        return new RealtimeMessage("PONG", "system", "pong");
    }

    /**
     * 创建错误消息
     */
    public static RealtimeMessage error(String topic, String errorMessage) {
        return new RealtimeMessage("ERROR", topic, errorMessage);
    }
}

