package com.weili.iot_portal.service.ingestion;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.handler.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.registry.WebhookHandlerRegistry;
import com.weili.iot_portal.service.ingestion.support.DeviceMatchingService;
import com.weili.iot_portal.service.ingestion.support.RealtimeWebhookCacheService;
import com.weili.iot_portal.service.ingestion.support.WebhookIdempotentService;
import com.weili.iot_portal.service.ingestion.support.WebhookInboxService;
import com.weili.iot_portal.service.ingestion.support.WebhookSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Webhook端到端测试
 * 覆盖TC-E2E-001 ~ TC-E2E-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook端到端测试")
class WebhookEndToEndTest {

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

    @Mock
    private WebhookHandlerRegistry handlerRegistry;

    @Mock
    private WebhookEventHandler stateHandler;

    @InjectMocks
    private WebhookReceiveService webhookReceiveService;

    @InjectMocks
    private WebhookProcessWorker webhookProcessWorker;

    private DeviceBaseInfoDO device;
    private String rawBody;

    @BeforeEach
    void setUp() {
        device = new DeviceBaseInfoDO();
        device.setDeviceCode("M001");
        device.setTbDeviceId("tb-device-001");
        device.setTenantUuid("tenant-001");

        rawBody = "{\"messageId\":\"msg-001\",\"deviceCode\":\"M001\"}";

        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(idempotentService.tryConsume(anyString())).thenReturn(true);
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
    }

    @Test
    @DisplayName("TC-E2E-001: 完整Webhook流程")
    void testCompleteWebhookFlow() {
        // Given - 完整的Webhook请求
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setDeviceCode("M001");

        // When - 执行完整的Webhook流程
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_STATE", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证整个流程
        // 1. 安全验证
        verify(securityService).validate("secret");
        verify(securityService).validateSignature("signature", "1234567890", "nonce", rawBody);

        // 2. 幂等性检查
        verify(idempotentService).tryConsume("msg-001");

        // 3. 设备匹配
        verify(deviceMatchingService).match("M001");

        // 4. 写入收件箱
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        verify(webhookInboxService).saveToInbox(requestCaptor.capture(), eq(device));

        // 5. 验证请求信息已补充
        WebhookRequest capturedRequest = requestCaptor.getValue();
        assertEquals("tb-device-001", capturedRequest.getDeviceId());
        assertEquals("tenant-001", capturedRequest.getTenantId());
        assertEquals("BUSINESS", capturedRequest.getWebhookCategory());
        assertEquals("DEVICE_STATE", capturedRequest.getEventType());
    }

    @Test
    @DisplayName("TC-E2E-002: 状态事件完整流程")
    void testDeviceStateEventFlow() {
        // Given - 状态事件
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-state-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_STATE");

        // When - 接收并处理
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_STATE", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证收件箱保存
        verify(webhookInboxService).saveToInbox(any(WebhookRequest.class), eq(device));

        // 模拟Worker处理
        // 注意：实际测试中，Worker会从收件箱获取消息并调用Handler处理
        // 这里主要验证收件箱保存成功，Worker处理在WebhookProcessWorkerTest中已测试
    }

    @Test
    @DisplayName("TC-E2E-003: 产量事件完整流程")
    void testDeviceProductionEventFlow() {
        // Given - 产量事件
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-production-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_PRODUCTION");

        // When - 接收并处理
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证收件箱保存
        verify(webhookInboxService).saveToInbox(any(WebhookRequest.class), eq(device));

        // 注意：产量记录的汇总和指标计算在定时任务中处理，这里主要验证收件箱保存
    }

    @Test
    @DisplayName("TC-E2E-004: 报警事件完整流程")
    void testDeviceAlarmEventFlow() {
        // Given - 报警事件
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-alarm-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_ALARM");

        // When - 接收并处理
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_ALARM", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证收件箱保存
        verify(webhookInboxService).saveToInbox(any(WebhookRequest.class), eq(device));

        // 注意：报警历史记录更新在Handler中处理，这里主要验证收件箱保存
    }

    @Test
    @DisplayName("TC-E2E-005: 实时数据完整流程")
    void testRealtimeDataFlow() {
        // Given - 实时数据事件
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-realtime-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_AXIS");

        // When - 接收实时数据
        webhookReceiveService.handle(
                "REALTIME", "DEVICE_AXIS", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证直接写入Redis缓存（不经过收件箱）
        verify(realtimeWebhookCacheService).cache(eq("DEVICE_AXIS"), eq("M001"), any(WebhookRequest.class));
        verify(webhookInboxService, never()).saveToInbox(any(), any());
    }

    @Test
    @DisplayName("端到端测试 - 设备未匹配场景")
    void testUnmatchedDeviceFlow() {
        // Given - 设备未匹配
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.empty());

        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-unknown-001");
        request.setDeviceCode("UNKNOWN_DEVICE");

        // When - 接收Webhook
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_STATE", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 应该直接ACK，不保存到收件箱
        verify(webhookInboxService, never()).saveToInbox(any(), any());
        verify(realtimeWebhookCacheService, never()).cache(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("端到端测试 - 幂等性跳过")
    void testIdempotentSkipFlow() {
        // Given - 消息已处理过
        when(idempotentService.tryConsume(anyString())).thenReturn(false);

        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-duplicate-001");
        request.setDeviceCode("M001");

        // When - 接收重复的Webhook
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_STATE", rawBody, request,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 应该跳过处理
        verify(webhookInboxService, never()).saveToInbox(any(), any());
        verify(deviceMatchingService, never()).match(anyString());
    }
}

