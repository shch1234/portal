package com.weili.iot_portal.web.realtime.handler;

import com.alibaba.fastjson2.JSON;
import com.weili.iot_portal.service.realtime.RealtimePushService;
import com.weili.iot_portal.service.realtime.impl.RealtimePushServiceImpl;
import com.weili.iot_portal.service.realtime.model.RealtimeMessage;
import com.weili.iot_portal.service.realtime.model.RealtimeSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时推送WebSocket处理器（通用）
 * 
 * <p>处理WebSocket连接、消息收发、订阅管理等
 */
@Slf4j
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimePushService realtimePushService;

    /**
     * 会话存储：会话ID -> WebSocketSession
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(RealtimePushService realtimePushService) {
        this.realtimePushService = realtimePushService;
        // 设置SessionProvider
        if (realtimePushService instanceof RealtimePushServiceImpl) {
            ((RealtimePushServiceImpl) realtimePushService).setSessionProvider(this::getSession);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket连接建立: sessionId={}, remoteAddress={}", sessionId, session.getRemoteAddress());

        // 发送连接确认消息
        sendMessage(session, RealtimeMessage.subscribed("system"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        log.debug("收到WebSocket消息: sessionId={}, payload={}", sessionId, payload);

        try {
            RealtimeMessage wsMessage = JSON.parseObject(payload, RealtimeMessage.class);
            if (wsMessage == null || wsMessage.getType() == null) {
                sendError(session, "消息格式错误");
                return;
            }

            handleMessage(session, sessionId, wsMessage);
        } catch (Exception e) {
            log.error("处理WebSocket消息失败: sessionId={}, payload={}", sessionId, payload, e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理收到的消息
     */
    private void handleMessage(WebSocketSession session, String sessionId, RealtimeMessage wsMessage) {
        String type = wsMessage.getType();
        String topic = wsMessage.getTopic();

        switch (type) {
            case "SUBSCRIBE":
                handleSubscribe(session, sessionId, topic);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(session, sessionId, topic);
                break;
            case "PING":
                handlePing(session, sessionId);
                break;
            default:
                log.warn("未知的消息类型: type={}, sessionId={}", type, sessionId);
                sendError(session, "未知的消息类型: " + type);
        }
    }

    /**
     * 处理订阅请求
     */
    private void handleSubscribe(WebSocketSession session, String sessionId, String topic) {
        // 解析订阅参数（从查询参数或消息中获取）
        String tenantId = getQueryParam(session, "tenantId");
        String factoryId = getQueryParam(session, "factoryId");
        String workshopId = getQueryParam(session, "workshopId");

        // 构建订阅信息
        RealtimeSubscription subscription = new RealtimeSubscription();
        subscription.setTopic(topic);
        subscription.setSessionId(sessionId);
        subscription.setTenantId(tenantId);

        // 如果主题包含参数，解析参数
        if (topic != null && topic.contains(":")) {
            // 主题格式：topic:param1:param2:...
            String[] parts = topic.split(":", 2);
            subscription.setTopic(parts[0]);
            if (parts.length > 1) {
                // 可以使用参数构建订阅键
            }
        }

        // 添加到订阅
        realtimePushService.subscribe(subscription);

        // 发送订阅确认
        RealtimeMessage subscribed = RealtimeMessage.subscribed(topic);
        sendMessage(session, subscribed);

        log.info("订阅成功: sessionId={}, topic={}", sessionId, topic);
    }

    /**
     * 处理取消订阅请求
     */
    private void handleUnsubscribe(WebSocketSession session, String sessionId, String topic) {
        RealtimeSubscription subscription = new RealtimeSubscription();
        subscription.setTopic(topic);
        subscription.setSessionId(sessionId);
        realtimePushService.unsubscribe(subscription);

        log.info("取消订阅: sessionId={}, topic={}", sessionId, topic);
    }

    /**
     * 处理心跳请求
     */
    private void handlePing(WebSocketSession session, String sessionId) {
        realtimePushService.sendPong(sessionId);
        log.debug("收到心跳: sessionId={}", sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        realtimePushService.removeAllSubscriptions(sessionId);
        log.info("WebSocket连接关闭: sessionId={}, status={}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session.getId();
        log.error("WebSocket传输错误: sessionId={}", sessionId, exception);
        sessions.remove(sessionId);
        realtimePushService.removeAllSubscriptions(sessionId);
    }

    /**
     * 获取查询参数
     */
    private String getQueryParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String query = uri.getQuery();
        String[] params = query.split("&");
        for (String param : params) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return kv[1];
            }
        }
        return null;
    }

    /**
     * 发送消息
     */
    private void sendMessage(WebSocketSession session, RealtimeMessage message) {
        try {
            String json = JSON.toJSONString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送WebSocket消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        RealtimeMessage error = RealtimeMessage.error("system", errorMessage);
        sendMessage(session, error);
    }

    /**
     * 获取会话（供RealtimePushService使用）
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}

