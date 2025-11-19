package com.weili.iot_portal.service.realtime;

import com.weili.iot_portal.service.realtime.model.RealtimeMessage;
import com.weili.iot_portal.service.realtime.model.RealtimeSubscription;

import java.util.Set;

/**
 * 实时推送服务接口（通用）
 * 
 * <p>提供统一的实时数据推送能力，支持多种业务场景：
 * - 报警数量变化推送
 * - 设备状态变化推送
 * - 设备指标变化推送
 * - 其他实时数据推送
 */
public interface RealtimePushService {

    /**
     * 添加订阅
     *
     * @param subscription 订阅信息
     */
    void subscribe(RealtimeSubscription subscription);

    /**
     * 取消订阅
     *
     * @param subscription 订阅信息
     */
    void unsubscribe(RealtimeSubscription subscription);

    /**
     * 移除会话的所有订阅（连接断开时）
     *
     * @param sessionId 会话ID
     */
    void removeAllSubscriptions(String sessionId);

    /**
     * 推送消息到订阅的客户端
     *
     * @param topic 消息主题
     * @param message 消息内容
     */
    void push(String topic, RealtimeMessage message);

    /**
     * 推送数据变化消息
     *
     * @param topic 消息主题
     * @param data 变化的数据
     */
    void pushDataChanged(String topic, Object data);

    /**
     * 构建主题键
     *
     * @param topicPrefix 主题前缀（如：alarm.count, device.state）
     * @param params 参数数组（按顺序排列）
     * @return 主题键
     */
    String buildTopicKey(String topicPrefix, String... params);

    /**
     * 获取订阅某个主题的所有会话ID
     *
     * @param topicKey 主题键
     * @return 会话ID集合
     */
    Set<String> getSubscribedSessions(String topicKey);

    /**
     * 发送心跳响应
     *
     * @param sessionId 会话ID
     */
    void sendPong(String sessionId);
}

