package com.weili.iot_portal.service.ingestion;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.support.DeviceMatchingService;
import com.weili.iot_portal.service.ingestion.support.RealtimeWebhookCacheService;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook接收服务测试
 * 覆盖 Webhook 接收流程的核心逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook接收服务测试")
class WebhookReceiveServiceTest {

    @Mock
    private WebhookSecurityService securityService;

    @Mock
    private WebhookIdempotentService idempotentService;

    @Mock
    private DeviceMatchingService deviceMatchingService;

    @Mock
    private WebhookInboxService webhookInboxService;

    @Mock
    private RealtimeWebhookCacheService realtimeWebhookCacheService;

    @InjectMocks
    private WebhookReceiveService webhookReceiveService;

    private WebhookRequest request;
    private DeviceBaseInfoDO device;
    private String rawBody;
    private String signature;
    private String timestamp;
    private String nonce;

    @BeforeEach
    void setUp() {
        rawBody = "{\"messageId\":\"msg-001\",\"deviceCode\":\"M001\"}";
        signature = "test-signature";
        timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        nonce = "test-nonce";

        request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_STATE");

        device = new DeviceBaseInfoDO();
        device.setId("device-info-001");
        device.setDeviceCode("M001");
        device.setTenantUuid("tenant-001");
        device.setTbDeviceId("tb-device-001");
        device.setDeviceStatus("ACTIVE");
        device.setIsMonitored(Boolean.TRUE);
    }

    @Test
    @DisplayName("正常处理BUSINESS类型消息")
    void testHandleBusinessMessage() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(webhookInboxService).saveToInbox(any(), any());

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        verify(securityService).validate("header-secret");
        verify(securityService).validateSignature(signature, timestamp, nonce, rawBody);
        verify(idempotentService).tryConsume("msg-001");
        verify(deviceMatchingService).match("M001");
        verify(webhookInboxService).saveToInbox(any(WebhookRequest.class), eq(device));
        verify(realtimeWebhookCacheService, never()).cache(anyString(), anyString(), any());
        assertEquals("BUSINESS", request.getWebhookCategory());
        assertEquals("DEVICE_STATE", request.getEventType());
        assertEquals("tb-device-001", request.getDeviceId());
        assertEquals("tenant-001", request.getTenantId());
    }

    @Test
    @DisplayName("正常处理REALTIME类型消息")
    void testHandleRealtimeMessage() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(realtimeWebhookCacheService).cache(anyString(), anyString(), any());

        // When
        webhookReceiveService.handle("REALTIME", "DEVICE_AXIS", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        verify(webhookInboxService, never()).saveToInbox(any(), any());
        verify(realtimeWebhookCacheService).cache("DEVICE_AXIS", "M001", request);
        assertEquals("REALTIME", request.getWebhookCategory());
        assertEquals("DEVICE_AXIS", request.getEventType());
    }

    @Test
    @DisplayName("幂等性检查失败，跳过处理")
    void testIdempotentCheckFail() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(false);

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        verify(idempotentService).tryConsume("msg-001");
        verify(deviceMatchingService, never()).match(anyString());
        verify(webhookInboxService, never()).saveToInbox(any(), any());
        verify(realtimeWebhookCacheService, never()).cache(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("设备匹配失败，跳过处理")
    void testDeviceMatchingFail() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.empty());

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        verify(deviceMatchingService).match("M001");
        verify(webhookInboxService, never()).saveToInbox(any(), any());
        verify(realtimeWebhookCacheService, never()).cache(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("不支持的category抛出异常")
    void testUnsupportedCategory() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));

        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () ->
                webhookReceiveService.handle("INVALID", "DEVICE_STATE", rawBody, request,
                        "header-secret", signature, timestamp, nonce));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("不支持的 webhook category"));
    }

    @Test
    @DisplayName("补充设备信息 - deviceId为空")
    void testSupplementDeviceId() {
        // Given
        request.setDeviceId(null);
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(webhookInboxService).saveToInbox(any(), any());

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        assertEquals("tb-device-001", request.getDeviceId());
    }

    @Test
    @DisplayName("补充设备信息 - tenantId为空")
    void testSupplementTenantId() {
        // Given
        request.setTenantId(null);
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(webhookInboxService).saveToInbox(any(), any());

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        assertEquals("tenant-001", request.getTenantId());
    }

    @Test
    @DisplayName("不覆盖已有的deviceId和tenantId")
    void testNotOverrideExistingDeviceInfo() {
        // Given
        request.setDeviceId("existing-device-id");
        request.setTenantId("existing-tenant-id");
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(webhookInboxService).saveToInbox(any(), any());

        // When
        webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        assertEquals("existing-device-id", request.getDeviceId());
        assertEquals("existing-tenant-id", request.getTenantId());
    }

    @Test
    @DisplayName("安全验证失败抛出异常")
    void testSecurityValidationFail() {
        // Given
        doThrow(new ServiceException(401, "签名验证失败"))
                .when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());

        // When & Then
        assertThrows(ServiceException.class, () ->
                webhookReceiveService.handle("BUSINESS", "DEVICE_STATE", rawBody, request,
                        "header-secret", signature, timestamp, nonce));
        verify(idempotentService, never()).tryConsume(anyString());
    }

    @Test
    @DisplayName("处理大小写不敏感的category")
    void testCaseInsensitiveCategory() {
        // Given
        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
        doNothing().when(webhookInboxService).saveToInbox(any(), any());

        // When
        webhookReceiveService.handle("business", "DEVICE_STATE", rawBody, request,
                "header-secret", signature, timestamp, nonce);

        // Then
        verify(webhookInboxService).saveToInbox(any(), any());
    }
}

