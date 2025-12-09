package com.weili.iot_portal.service.ingestion.handler.impl;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志回退Webhook处理器测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("日志回退Webhook处理器测试")
class LoggingFallbackWebhookHandlerTest {

    @InjectMocks
    private LoggingFallbackWebhookHandler loggingFallbackWebhookHandler;

    private WebhookInboxDO inbox;
    private WebhookRequest request;

    @BeforeEach
    void setUp() {
        inbox = new WebhookInboxDO();
        inbox.setMessageId("msg-001");
        inbox.setStatus("PENDING");

        request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setEventType("UNKNOWN_EVENT");
        request.setDeviceCode("M001");
    }

    @Test
    @DisplayName("supports方法总是返回true")
    void testSupports() {
        // When & Then
        assertTrue(loggingFallbackWebhookHandler.supports("ANY_EVENT"));
        assertTrue(loggingFallbackWebhookHandler.supports("DEVICE_STATE"));
        assertTrue(loggingFallbackWebhookHandler.supports(null));
        assertTrue(loggingFallbackWebhookHandler.supports(""));
    }

    @Test
    @DisplayName("handle方法抛出ServiceException")
    void testHandleThrowsException() {
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                loggingFallbackWebhookHandler.handle(inbox, request));

        assertEquals(404, exception.getCode());
        assertTrue(exception.getMessage().contains("Unsupported eventType"));
        assertTrue(exception.getMessage().contains("UNKNOWN_EVENT"));
    }

    @Test
    @DisplayName("handle方法 - 不同eventType")
    void testHandleWithDifferentEventTypes() {
        // Test 1
        request.setEventType("CUSTOM_EVENT");
        ServiceException exception1 = assertThrows(ServiceException.class, () ->
                loggingFallbackWebhookHandler.handle(inbox, request));
        assertTrue(exception1.getMessage().contains("CUSTOM_EVENT"));

        // Test 2
        request.setEventType("ANOTHER_EVENT");
        ServiceException exception2 = assertThrows(ServiceException.class, () ->
                loggingFallbackWebhookHandler.handle(inbox, request));
        assertTrue(exception2.getMessage().contains("ANOTHER_EVENT"));
    }

    @Test
    @DisplayName("order方法返回9999")
    void testOrder() {
        // When
        int order = loggingFallbackWebhookHandler.order();

        // Then
        assertEquals(9999, order);
    }

    @Test
    @DisplayName("handle方法 - eventType为null")
    void testHandleWithNullEventType() {
        // Given
        request.setEventType(null);

        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                loggingFallbackWebhookHandler.handle(inbox, request));

        assertEquals(404, exception.getCode());
        assertTrue(exception.getMessage().contains("Unsupported eventType"));
    }

    @Test
    @DisplayName("作为兜底处理器，优先级最低")
    void testFallbackPriority() {
        // Given
        int order = loggingFallbackWebhookHandler.order();

        // Then - order应该是最大的，确保最后匹配
        assertTrue(order >= 9999);
    }
}

