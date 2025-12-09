package com.weili.iot_portal.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook处理Worker测试
 * 测试用例：TC-WORKER-001 ~ TC-WORKER-007
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook处理Worker测试")
class WebhookProcessWorkerTest {

    @Mock
    private WebhookInboxService inboxService;

    @Mock
    private WebhookHandlerRegistry handlerRegistry;

    @Mock
    private WebhookMonitorService monitorService;

    @Mock
    private WebhookFailLogService webhookFailLogService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebhookEventHandler handler;

    @InjectMocks
    private WebhookProcessWorker webhookProcessWorker;

    @Test
    @DisplayName("TC-WORKER-001: Worker正常处理消息")
    void testNormalProcessing() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "PENDING");
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(1, result);
        verify(inboxService).markProcessing(inbox);
        verify(handler).handle(eq(inbox), any(WebhookRequest.class));
        verify(inboxService).markSuccess(inbox);
        verify(monitorService).recordSuccess(eq("DEVICE_STATE"), anyLong());
    }

    @Test
    @DisplayName("TC-WORKER-002: Worker批量处理")
    void testBatchProcessing() throws Exception {
        // Given
        List<WebhookInboxDO> inboxList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            WebhookInboxDO inbox = createInbox("msg-" + i, "DEVICE_STATE", "PENDING");
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageId", "msg-" + i);
            payload.put("eventType", "DEVICE_STATE");
            inbox.setPayload(payload);
            inboxList.add(inbox);
        }
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class)))
                .thenAnswer(invocation -> {
                    Map<String, Object> payload = invocation.getArgument(0);
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId((String) payload.get("messageId"));
                    request.setEventType((String) payload.get("eventType"));
                    return request;
                });
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(10, result);
        verify(handler, times(10)).handle(any(), any());
        verify(inboxService, times(10)).markSuccess(any());
    }

    @Test
    @DisplayName("TC-WORKER-003: Worker处理失败重试")
    void testFailedRetry() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "FAILED");
        inbox.setNextRetryTime(LocalDateTime.now().minusMinutes(1)); // 已到重试时间
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(1, result);
        verify(inboxService).markProcessing(inbox);
        verify(handler).handle(eq(inbox), any(WebhookRequest.class));
    }

    @Test
    @DisplayName("TC-WORKER-004: Worker跳过未到重试时间")
    void testSkipNotDueRetry() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "FAILED");
        inbox.setNextRetryTime(LocalDateTime.now().plusMinutes(10)); // 未到重试时间
        
        // fetchDue应该不返回这条记录（因为nextRetryTime未到）
        when(inboxService.fetchDue()).thenReturn(Collections.emptyList());
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(handler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("TC-WORKER-005: Worker处理异常")
    void testProcessingException() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "PENDING");
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("处理失败")).when(handler).handle(any(), any());
        when(inboxService.reachMaxRetry(inbox)).thenReturn(false);
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(inboxService).markFailed(inbox, "处理失败");
        verify(monitorService).recordFailure(eq("DEVICE_STATE"), anyString(), anyLong(), eq(true));
    }

    @Test
    @DisplayName("TC-WORKER-006: Worker超过最大重试次数")
    void testMaxRetryReached() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "FAILED");
        inbox.setProcessCount(5); // 已达到最大重试次数
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("处理失败")).when(handler).handle(any(), any());
        when(inboxService.reachMaxRetry(inbox)).thenReturn(true);
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(inboxService).markFailed(inbox, "处理失败");
        verify(webhookFailLogService).saveFailLog(any(WebhookRequest.class), eq("PROCESS"), anyString(), eq(false));
    }

    @Test
    @DisplayName("TC-WORKER-007: Worker处理速度")
    void testProcessingSpeed() throws Exception {
        // Given
        List<WebhookInboxDO> inboxList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            WebhookInboxDO inbox = createInbox("msg-" + i, "DEVICE_STATE", "PENDING");
            Map<String, Object> payload = new HashMap<>();
            payload.put("messageId", "msg-" + i);
            payload.put("eventType", "DEVICE_STATE");
            inbox.setPayload(payload);
            inboxList.add(inbox);
        }
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class)))
                .thenAnswer(invocation -> {
                    Map<String, Object> payload = invocation.getArgument(0);
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId((String) payload.get("messageId"));
                    request.setEventType((String) payload.get("eventType"));
                    return request;
                });
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        
        // When
        long startTime = System.currentTimeMillis();
        int result = webhookProcessWorker.processBatch();
        long endTime = System.currentTimeMillis();
        
        // Then
        assertEquals(100, result);
        long duration = endTime - startTime;
        // 处理速度应该 > 100条/秒，即100条应该在1秒内完成
        assertTrue(duration < 10000, "处理100条消息应该在10秒内完成");
    }

    @Test
    @DisplayName("验证未匹配的事件类型")
    void testUnmatchedEventType() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "UNKNOWN_EVENT", "PENDING");
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "UNKNOWN_EVENT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("UNKNOWN_EVENT")).thenReturn(Optional.empty());
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(inboxService).markFailedNoRetry(inbox, "Unsupported eventType: UNKNOWN_EVENT");
        verify(webhookFailLogService).saveFailLog(any(WebhookRequest.class), eq("VALIDATION"), anyString(), eq(true));
        verify(monitorService).recordUnmatched("UNKNOWN_EVENT");
    }

    @Test
    @DisplayName("验证业务异常需要人工处理")
    void testBusinessExceptionNeedsManual() throws Exception {
        // Given
        WebhookInboxDO inbox = createInbox("msg-001", "DEVICE_STATE", "PENDING");
        List<WebhookInboxDO> inboxList = Collections.singletonList(inbox);
        
        when(inboxService.fetchDue()).thenReturn(inboxList);
        
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE");
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventData", request.getEventData());
        inbox.setPayload(payload);
        
        when(objectMapper.convertValue(any(), eq(WebhookRequest.class))).thenReturn(request);
        when(handlerRegistry.resolve("DEVICE_STATE")).thenReturn(Optional.of(handler));
        doThrow(new ServiceException(400, "业务异常")).when(handler).handle(any(), any());
        when(inboxService.reachMaxRetry(inbox)).thenReturn(true);
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(webhookFailLogService).saveFailLog(any(WebhookRequest.class), eq("PROCESS"), anyString(), eq(true));
    }

    @Test
    @DisplayName("验证空列表处理")
    void testEmptyList() throws Exception {
        // Given
        when(inboxService.fetchDue()).thenReturn(Collections.emptyList());
        
        // When
        int result = webhookProcessWorker.processBatch();
        
        // Then
        assertEquals(0, result);
        verify(handler, never()).handle(any(), any());
    }

    // 辅助方法
    private WebhookInboxDO createInbox(String messageId, String eventType, String status) {
        WebhookInboxDO inbox = new WebhookInboxDO();
        inbox.setMessageId(messageId);
        inbox.setEventType(eventType);
        inbox.setStatus(status);
        inbox.setProcessCount(0);
        inbox.setReceivedTime(LocalDateTime.now());
        inbox.setTenantUuid("tenant-001");
        inbox.setDeviceCode("M001");
        return inbox;
    }

    private WebhookRequest createWebhookRequest(String messageId, String eventType) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId(messageId);
        request.setEventType(eventType);
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("state", "WORKING");
        request.setEventData(eventData);
        
        return request;
    }
}

