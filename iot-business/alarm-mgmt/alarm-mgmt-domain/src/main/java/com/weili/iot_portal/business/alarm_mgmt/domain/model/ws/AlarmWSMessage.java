package com.weili.iot_portal.business.alarm_mgmt.domain.model.ws;

import com.weili.iot_portal.business.alarm_mgmt.domain.model.CurrentAlarmDeviceCountVO;
import lombok.Data;

import java.io.Serializable;

/**
 * WebSocket消息模型
 */
@Data
public class AlarmWSMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息负载（根据type不同，内容不同）
     */
    private Object payload;

    /**
     * 时间戳
     */
    private Long timestamp;

    public AlarmWSMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public AlarmWSMessage(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /**
         * 订阅请求
         */
        SUBSCRIBE,

        /**
         * 取消订阅
         */
        UNSUBSCRIBE,

        /**
         * 订阅确认
         */
        SUBSCRIBED,

        /**
         * 报警数量变化
         */
        ALARM_COUNT_CHANGED,

        /**
         * 心跳
         */
        PING,

        /**
         * 心跳响应
         */
        PONG,

        /**
         * 错误
         */
        ERROR
    }

    /**
     * 创建报警数量变化消息
     */
    public static AlarmWSMessage alarmCountChanged(CurrentAlarmDeviceCountVO count) {
        return new AlarmWSMessage(MessageType.ALARM_COUNT_CHANGED, count);
    }

    /**
     * 创建订阅确认消息
     */
    public static AlarmWSMessage subscribed(String message) {
        return new AlarmWSMessage(MessageType.SUBSCRIBED, message);
    }

    /**
     * 创建错误消息
     */
    public static AlarmWSMessage error(String errorMessage) {
        return new AlarmWSMessage(MessageType.ERROR, errorMessage);
    }

    /**
     * 创建心跳响应
     */
    public static AlarmWSMessage pong() {
        return new AlarmWSMessage(MessageType.PONG, "pong");
    }
}

