package com.weili.iot_portal.service.realtime.impl;

import com.alibaba.fastjson2.JSON;
import com.weili.iot_portal.service.realtime.RealtimePushService;
import com.weili.iot_portal.service.realtime.model.RealtimeMessage;
import com.weili.iot_portal.service.realtime.model.RealtimeSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送服务实现（通用）
 */
@Slf4j
@Service
public class RealtimePushServiceImpl implements RealtimePushService {

    /**
     * 订阅管理：主题键 -> 会话ID集合
     */
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 会话管理：会话ID -> 订阅的主题键集合
     */
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    /**
     * 会话存储：会话ID -> WebSocketSession
     * 注意：这里需要从WebSocketHandler中获取，不能直接存储
     * 可以通过回调或者通过WebSocketHandler管理
     */
    // private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * WebSocket会话提供者接口（由WebSocketHandler实现）
     */
    @FunctionalInterface
    public interface SessionProvider {
        WebSocketSession getSession(String sessionId);
    }

    private SessionProvider sessionProvider;

    public void setSessionProvider(SessionProvider sessionProvider) {
        this.sessionProvider = sessionProvider;
    }

    @Override
    public void subscribe(RealtimeSubscription subscription) {
        String topicKey = subscription.getSubscriptionKey();
        String sessionId = subscription.getSessionId();

        // 添加到订阅映射
        subscriptions.computeIfAbsent(topicKey, k -> ConcurrentHashMap.newKeySet())
                     .add(sessionId);

        // 添加到会话订阅映射
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                           .add(topicKey);

        log.debug("订阅成功: topicKey={}, sessionId={}", topicKey, sessionId);
    }

    @Override
    public void unsubscribe(RealtimeSubscription subscription) {
        String topicKey = subscription.getSubscriptionKey();
        String sessionId = subscription.getSessionId();

        // 从订阅映射中移除
        Set<String> sessions = subscriptions.get(topicKey);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                subscriptions.remove(topicKey);
            }
        }

        // 从会话订阅映射中移除
        Set<String> topics = sessionSubscriptions.get(sessionId);
        if (topics != null) {
            topics.remove(topicKey);
            if (topics.isEmpty()) {
                sessionSubscriptions.remove(sessionId);
            }
        }

        log.debug("取消订阅: topicKey={}, sessionId={}", topicKey, sessionId);
    }

    @Override
    public void removeAllSubscriptions(String sessionId) {
        // 获取该会话的所有订阅
        Set<String> topics = sessionSubscriptions.remove(sessionId);
        if (topics != null) {
            // 从所有订阅中移除该会话
            for (String topicKey : topics) {
                Set<String> sessions = subscriptions.get(topicKey);
                if (sessions != null) {
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        subscriptions.remove(topicKey);
                    }
                }
            }
        }
        log.debug("移除会话的所有订阅: sessionId={}", sessionId);
    }

    @Override
    public void push(String topic, RealtimeMessage message) {
        Set<String> sessionIds = subscriptions.get(topic);
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("没有订阅该主题的会话: topic={}", topic);
            return;
        }

        String messageJson = JSON.toJSONString(message);
        TextMessage textMessage = new TextMessage(messageJson);

        int successCount = 0;
        int failCount = 0;

        for (String sessionId : new ArrayList<>(sessionIds)) {
            if (sendMessage(sessionId, textMessage)) {
                successCount++;
            } else {
                failCount++;
                // 发送失败，可能连接已断开，移除订阅
                removeSessionSubscription(topic, sessionId);
            }
        }

        log.debug("推送消息: topic={}, 成功={}, 失败={}", topic, successCount, failCount);
    }

    @Override
    public void pushDataChanged(String topic, Object data) {
        RealtimeMessage message = RealtimeMessage.dataChanged(topic, data);
        push(topic, message);
    }

    @Override
    public String buildTopicKey(String topicPrefix, String... params) {
        StringBuilder key = new StringBuilder(topicPrefix);
        for (String param : params) {
            if (param != null) {
                key.append(":").append(param);
            }
        }
        return key.toString();
    }

    @Override
    public Set<String> getSubscribedSessions(String topicKey) {
        Set<String> sessions = subscriptions.get(topicKey);
        return sessions != null ? new HashSet<>(sessions) : Collections.emptySet();
    }

    @Override
    public void sendPong(String sessionId) {
        RealtimeMessage message = RealtimeMessage.pong();
        String messageJson = JSON.toJSONString(message);
        TextMessage textMessage = new TextMessage(messageJson);
        sendMessage(sessionId, textMessage);
    }

    /**
     * 发送消息到指定会话
     */
    private boolean sendMessage(String sessionId, TextMessage message) {
        if (sessionProvider == null) {
            log.warn("SessionProvider未设置，无法发送消息: sessionId={}", sessionId);
            return false;
        }

        WebSocketSession session = sessionProvider.getSession(sessionId);
        if (session == null || !session.isOpen()) {
            log.debug("会话不存在或已关闭: sessionId={}", sessionId);
            return false;
        }

        try {
            session.sendMessage(message);
            return true;
        } catch (IOException e) {
            log.error("发送WebSocket消息失败: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 移除会话订阅（发送失败时调用）
     */
    private void removeSessionSubscription(String topicKey, String sessionId) {
        Set<String> sessions = subscriptions.get(topicKey);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                subscriptions.remove(topicKey);
            }
        }

        Set<String> topics = sessionSubscriptions.get(sessionId);
        if (topics != null) {
            topics.remove(topicKey);
        }
    }
}

