package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.mapper.ingestion.WebhookInboxMapper;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Webhook收件箱服务测试
 * 测试用例：TC-INBOX-001 ~ TC-INBOX-007
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook收件箱服务测试")
class WebhookInboxServiceTest {

    @Mock
    private WebhookInboxMapper inboxMapper;

    @Mock
    private WebhookFailLogService webhookFailLogService;

    @InjectMocks
    private WebhookInboxService webhookInboxService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookInboxService, "batchSize", 100);
        ReflectionTestUtils.setField(webhookInboxService, "maxRetryCount", 5);
        ReflectionTestUtils.setField(webhookInboxService, "retryIntervalBaseSeconds", 60);
    }

    @Test
    @DisplayName("TC-INBOX-001: 保存消息到收件箱")
    void testSaveToInbox() {
        // Given
        WebhookRequest request = createWebhookRequest("msg-001", "DEVICE_STATE", "BUSINESS");
        DeviceBaseInfoDO device = createDevice("M001");
        
        // When
        webhookInboxService.saveToInbox(request, device);
        
        // Then
        ArgumentCaptor<WebhookInboxDO> captor = ArgumentCaptor.forClass(WebhookInboxDO.class);
        verify(inboxMapper).insert(captor.capture());
        
        WebhookInboxDO saved = captor.getValue();
        assertEquals("msg-001", saved.getMessageId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, saved.getProcessCount());
        assertNotNull(saved.getReceivedTime());
        assertNotNull(saved.getPayload());
    }

    @Test
    @DisplayName("TC-INBOX-002: 查询待处理消息")
    void testFetchDueMessages() {
        // Given
        List<WebhookInboxDO> pendingMessages = Arrays.asList(
                createInboxDO("msg-001", "PENDING"),
                createInboxDO("msg-002", "PENDING")
        );
        
        when(inboxMapper.selectList(any())).thenReturn(pendingMessages);
        
        // When
        List<WebhookInboxDO> result = webhookInboxService.fetchDue();
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(inboxMapper).selectList(any());
    }

    @Test
    @DisplayName("TC-INBOX-003: 标记消息为处理中")
    void testMarkProcessing() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "PENDING");
        
        // When
        webhookInboxService.markProcessing(inbox);
        
        // Then
        assertEquals("PROCESSING", inbox.getStatus());
        assertNotNull(inbox.getUpdateTime());
        verify(inboxMapper).updateById(inbox);
    }

    @Test
    @DisplayName("TC-INBOX-004: 标记消息为成功")
    void testMarkSuccess() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "PROCESSING");
        
        // When
        webhookInboxService.markSuccess(inbox);
        
        // Then
        assertEquals("SUCCESS", inbox.getStatus());
        assertNotNull(inbox.getProcessedTime());
        assertNotNull(inbox.getUpdateTime());
        verify(inboxMapper).updateById(inbox);
    }

    @Test
    @DisplayName("TC-INBOX-005: 标记消息为失败")
    void testMarkFailed() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "PROCESSING");
        inbox.setProcessCount(0);
        String errorMessage = "处理失败";
        
        // When
        webhookInboxService.markFailed(inbox, errorMessage);
        
        // Then
        assertEquals("FAILED", inbox.getStatus());
        assertEquals(1, inbox.getProcessCount());
        assertEquals(errorMessage, inbox.getLastError());
        assertNotNull(inbox.getNextRetryTime());
        verify(inboxMapper).updateById(inbox);
    }

    @Test
    @DisplayName("TC-INBOX-006: 批量查询限制")
    void testBatchSizeLimit() {
        // Given
        List<WebhookInboxDO> messages = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            messages.add(createInboxDO("msg-" + i, "PENDING"));
        }
        
        when(inboxMapper.selectList(any())).thenReturn(messages.subList(0, 100));
        
        // When
        List<WebhookInboxDO> result = webhookInboxService.fetchDue();
        
        // Then
        assertNotNull(result);
        assertTrue(result.size() <= 100);
    }

    @Test
    @DisplayName("TC-INBOX-007: 重试时间计算")
    void testRetryTimeCalculation() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "PROCESSING");
        String errorMessage = "处理失败";
        
        // 第一次失败
        inbox.setProcessCount(0);
        webhookInboxService.markFailed(inbox, errorMessage);
        LocalDateTime firstRetryTime = inbox.getNextRetryTime();
        assertNotNull(firstRetryTime);
        
        // 第二次失败
        inbox.setProcessCount(1);
        webhookInboxService.markFailed(inbox, errorMessage);
        LocalDateTime secondRetryTime = inbox.getNextRetryTime();
        assertTrue(secondRetryTime.isAfter(firstRetryTime));
        
        // 第三次失败
        inbox.setProcessCount(2);
        webhookInboxService.markFailed(inbox, errorMessage);
        LocalDateTime thirdRetryTime = inbox.getNextRetryTime();
        assertTrue(thirdRetryTime.isAfter(secondRetryTime));
    }

    @Test
    @DisplayName("验证超过最大重试次数")
    void testReachMaxRetry() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "FAILED");
        inbox.setProcessCount(5);
        
        // When
        boolean result = webhookInboxService.reachMaxRetry(inbox);
        
        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("验证未超过最大重试次数")
    void testNotReachMaxRetry() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "FAILED");
        inbox.setProcessCount(3);
        
        // When
        boolean result = webhookInboxService.reachMaxRetry(inbox);
        
        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("验证保存时messageId为空")
    void testSaveWithEmptyMessageId() {
        // Given
        WebhookRequest request = new WebhookRequest();
        request.setMessageId(null);
        DeviceBaseInfoDO device = createDevice("M001");
        
        // When & Then
        assertThrows(ServiceException.class, () -> {
            webhookInboxService.saveToInbox(request, device);
        });
    }

    @Test
    @DisplayName("验证标记失败不重试")
    void testMarkFailedNoRetry() {
        // Given
        WebhookInboxDO inbox = createInboxDO("msg-001", "PROCESSING");
        String errorMessage = "设备未匹配";
        
        // When
        webhookInboxService.markFailedNoRetry(inbox, errorMessage);
        
        // Then
        assertEquals("FAILED", inbox.getStatus());
        assertEquals(5, inbox.getProcessCount()); // 直接设置为最大重试次数
        assertNull(inbox.getNextRetryTime()); // 不设置重试时间
        verify(inboxMapper).updateById(inbox);
    }

    // 辅助方法
    private WebhookRequest createWebhookRequest(String messageId, String eventType, String category) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId(messageId);
        request.setTenantId("tenant-001");
        request.setDeviceId("tb-device-001");
        request.setDeviceCode("M001");
        request.setEventType(eventType);
        request.setWebhookCategory(category);
        request.setTimestamp(System.currentTimeMillis());
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("state", "WORKING");
        request.setEventData(eventData);
        
        return request;
    }

    private DeviceBaseInfoDO createDevice(String deviceCode) {
        DeviceBaseInfoDO device = new DeviceBaseInfoDO();
        device.setDeviceCode(deviceCode);
        device.setTenantUuid("tenant-001");
        device.setTbDeviceId("tb-device-001");
        return device;
    }

    private WebhookInboxDO createInboxDO(String messageId, String status) {
        WebhookInboxDO inbox = new WebhookInboxDO();
        inbox.setMessageId(messageId);
        inbox.setStatus(status);
        inbox.setProcessCount(0);
        inbox.setReceivedTime(LocalDateTime.now());
        return inbox;
    }
}

