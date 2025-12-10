package com.weili.iot_portal.service.alarm;

import com.weili.iot_portal.domain.alarm.CurrentAlarmDeviceCountVO;

import java.util.Set;

/**
 * 报警WebSocket服务接口
 * 
 * <p>负责管理WebSocket连接和推送消息
 */
public interface AlarmWebSocketService {

    /**
     * 添加WebSocket会话到订阅组
     *
     * @param subscriptionKey 订阅键（格式：tenantId:factoryId:workshopId 或 tenantId:factoryId）
     * @param sessionId 会话ID
     */
    void addSubscription(String subscriptionKey, String sessionId);

    /**
     * 移除WebSocket会话
     *
     * @param subscriptionKey 订阅键
     * @param sessionId 会话ID
     */
    void removeSubscription(String subscriptionKey, String sessionId);

    /**
     * 移除所有订阅（连接断开时）
     *
     * @param sessionId 会话ID
     */
    void removeAllSubscriptions(String sessionId);

    /**
     * 推送报警数量变化消息
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选，null表示推送整个工厂）
     * @param count 报警数量统计
     */
    void pushAlarmCountChanged(String factoryId, String workshopId, CurrentAlarmDeviceCountVO count);

    /**
     * 获取订阅该键的所有会话ID
     *
     * @param subscriptionKey 订阅键
     * @return 会话ID集合
     */
    Set<String> getSubscribedSessions(String subscriptionKey);

    /**
     * 构建订阅键
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID
     * @param workshopId 车间ID（可选）
     * @return 订阅键
     */
    String buildSubscriptionKey(String factoryId, String workshopId);
}

