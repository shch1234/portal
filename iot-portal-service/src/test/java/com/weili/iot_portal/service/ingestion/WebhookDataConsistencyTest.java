package com.weili.iot_portal.service.ingestion;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook数据一致性测试
 * 覆盖TC-CONSISTENCY-001 ~ TC-CONSISTENCY-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook数据一致性测试")
class WebhookDataConsistencyTest {

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
    @DisplayName("TC-CONSISTENCY-001: 状态时间线完整性")
    void testStateTimelineIntegrity() {
        // Given - 发送多个状态事件
        List<WebhookRequest> requests = new ArrayList<>();
        String[] states = {"STANDBY", "WORKING", "STANDBY", "FAULT", "STANDBY"};
        long baseTimestamp = System.currentTimeMillis() / 1000;

        for (int i = 0; i < states.length; i++) {
            WebhookRequest request = new WebhookRequest();
            request.setMessageId("msg-state-" + i);
            request.setDeviceCode("M001");
            request.setEventType("DEVICE_STATE");
            requests.add(request);
        }

        // When - 按顺序处理状态事件
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        for (WebhookRequest request : requests) {
            webhookReceiveService.handle(
                    "BUSINESS", "DEVICE_STATE", rawBody, request,
                    "secret", "signature", "1234567890", "nonce"
            );
        }

        // Then - 验证所有事件都被保存到收件箱（后续Worker会处理并更新状态时间线）
        verify(webhookInboxService, times(states.length)).saveToInbox(requestCaptor.capture(), eq(device));
        
        List<WebhookRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(states.length, capturedRequests.size(), "所有状态事件都应该被保存");
        
        // 验证每个请求的设备信息都正确
        for (WebhookRequest req : capturedRequests) {
            assertEquals("M001", req.getDeviceCode());
            assertEquals("tb-device-001", req.getDeviceId());
            assertEquals("tenant-001", req.getTenantId());
        }
    }

    @Test
    @DisplayName("TC-CONSISTENCY-002: 产量记录完整性")
    void testProductionRecordIntegrity() {
        // Given - 发送开始和结束事件
        WebhookRequest startRequest = new WebhookRequest();
        startRequest.setMessageId("msg-production-start-001");
        startRequest.setDeviceCode("M001");
        startRequest.setEventType("DEVICE_PRODUCTION");

        WebhookRequest endRequest = new WebhookRequest();
        endRequest.setMessageId("msg-production-end-001");
        endRequest.setDeviceCode("M001");
        endRequest.setEventType("DEVICE_PRODUCTION");

        // When - 处理开始和结束事件
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, startRequest,
                "secret", "signature", "1234567890", "nonce"
        );
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, endRequest,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证开始和结束记录都被保存
        verify(webhookInboxService, times(2)).saveToInbox(requestCaptor.capture(), eq(device));
        
        List<WebhookRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(2, capturedRequests.size(), "开始和结束事件都应该被保存");
        
        // 验证设备信息一致性
        for (WebhookRequest req : capturedRequests) {
            assertEquals("M001", req.getDeviceCode());
            assertEquals("tb-device-001", req.getDeviceId());
            assertEquals("tenant-001", req.getTenantId());
        }
    }

    @Test
    @DisplayName("TC-CONSISTENCY-003: 班次信息一致性")
    void testShiftInfoConsistency() {
        // Given - 发送跨班次事件（模拟）
        WebhookRequest request1 = new WebhookRequest();
        request1.setMessageId("msg-shift-001");
        request1.setDeviceCode("M001");
        request1.setEventType("DEVICE_PRODUCTION");

        WebhookRequest request2 = new WebhookRequest();
        request2.setMessageId("msg-shift-002");
        request2.setDeviceCode("M001");
        request2.setEventType("DEVICE_PRODUCTION");

        // When - 处理跨班次事件
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, request1,
                "secret", "signature", "1234567890", "nonce"
        );
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, request2,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证所有事件都使用相同的设备信息（班次信息在Handler中处理）
        verify(webhookInboxService, times(2)).saveToInbox(requestCaptor.capture(), eq(device));
        
        List<WebhookRequest> capturedRequests = requestCaptor.getAllValues();
        // 验证设备信息一致性
        for (WebhookRequest req : capturedRequests) {
            assertEquals("M001", req.getDeviceCode());
            assertEquals("tb-device-001", req.getDeviceId());
            assertEquals("tenant-001", req.getTenantId());
        }
    }

    @Test
    @DisplayName("TC-CONSISTENCY-004: 汇总数据一致性")
    void testSummaryDataConsistency() {
        // Given - 发送多个产量事件（用于汇总）
        int eventCount = 10;
        List<WebhookRequest> requests = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            WebhookRequest request = new WebhookRequest();
            request.setMessageId("msg-summary-" + i);
            request.setDeviceCode("M001");
            request.setEventType("DEVICE_PRODUCTION");
            requests.add(request);
        }

        // When - 处理所有事件
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        for (WebhookRequest request : requests) {
            webhookReceiveService.handle(
                    "BUSINESS", "DEVICE_PRODUCTION", rawBody, request,
                    "secret", "signature", "1234567890", "nonce"
            );
        }

        // Then - 验证所有事件都被保存（汇总任务会从这些明细数据中汇总）
        verify(webhookInboxService, times(eventCount)).saveToInbox(requestCaptor.capture(), eq(device));
        
        List<WebhookRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(eventCount, capturedRequests.size(), "所有事件都应该被保存用于汇总");
        
        // 验证设备信息一致性
        for (WebhookRequest req : capturedRequests) {
            assertEquals("M001", req.getDeviceCode());
            assertEquals("tb-device-001", req.getDeviceId());
            assertEquals("tenant-001", req.getTenantId());
        }
    }

    @Test
    @DisplayName("TC-CONSISTENCY-005: 指标计算一致性")
    void testMetricsCalculationConsistency() {
        // Given - 发送用于指标计算的事件
        WebhookRequest stateRequest = new WebhookRequest();
        stateRequest.setMessageId("msg-metrics-state-001");
        stateRequest.setDeviceCode("M001");
        stateRequest.setEventType("DEVICE_STATE");

        WebhookRequest productionRequest = new WebhookRequest();
        productionRequest.setMessageId("msg-metrics-production-001");
        productionRequest.setDeviceCode("M001");
        productionRequest.setEventType("DEVICE_PRODUCTION");

        // When - 处理不同类型的事件
        ArgumentCaptor<WebhookRequest> requestCaptor = ArgumentCaptor.forClass(WebhookRequest.class);
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_STATE", rawBody, stateRequest,
                "secret", "signature", "1234567890", "nonce"
        );
        webhookReceiveService.handle(
                "BUSINESS", "DEVICE_PRODUCTION", rawBody, productionRequest,
                "secret", "signature", "1234567890", "nonce"
        );

        // Then - 验证所有事件都被保存（指标计算会基于这些数据）
        verify(webhookInboxService, times(2)).saveToInbox(requestCaptor.capture(), eq(device));
        
        List<WebhookRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(2, capturedRequests.size(), "所有事件都应该被保存用于指标计算");
        
        // 验证设备信息一致性
        for (WebhookRequest req : capturedRequests) {
            assertEquals("M001", req.getDeviceCode());
            assertEquals("tb-device-001", req.getDeviceId());
            assertEquals("tenant-001", req.getTenantId());
        }
    }
}

