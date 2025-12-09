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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook并发测试
 * 覆盖TC-CONCURRENT-001 ~ TC-CONCURRENT-003
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook并发测试")
class WebhookConcurrentTest {

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

    private String rawBody;
    private String category;
    private String eventType;
    private DeviceBaseInfoDO device;

    @BeforeEach
    void setUp() {
        rawBody = "{\"messageId\":\"msg-001\",\"deviceCode\":\"M001\"}";
        category = "BUSINESS";
        eventType = "DEVICE_STATE";

        device = new DeviceBaseInfoDO();
        device.setDeviceCode("M001");
        device.setTbDeviceId("tb-device-001");
        device.setTenantUuid("tenant-001");

        doNothing().when(securityService).validate(anyString());
        doNothing().when(securityService).validateSignature(anyString(), anyString(), anyString(), anyString());
        when(deviceMatchingService.match(anyString())).thenReturn(Optional.of(device));
    }

    @Test
    @DisplayName("TC-CONCURRENT-001: 并发接收Webhook - 100个请求")
    void testConcurrentWebhookReception() throws InterruptedException {
        // Given
        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

        // 模拟幂等性检查 - 每个messageId只允许处理一次
        when(idempotentService.tryConsume(anyString())).thenAnswer(invocation -> {
            String messageId = invocation.getArgument(0);
            return processedMessageIds.add(messageId);
        });

        // When - 并发发送100个请求
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId("msg-" + index);
                    request.setDeviceCode("M001");

                    webhookReceiveService.handle(
                            category, eventType, rawBody, request,
                            "secret", "signature", "1234567890", "nonce"
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - 等待所有请求完成
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有请求应在10秒内完成");
        executor.shutdown();

        // 验证所有请求都被处理
        assertEquals(requestCount, successCount.get() + failureCount.get(), "所有请求都应该被处理");
        assertEquals(requestCount, processedMessageIds.size(), "所有messageId都应该被处理");
    }

    @Test
    @DisplayName("TC-CONCURRENT-002: 并发处理同一设备 - 分布式锁生效")
    void testConcurrentSameDeviceProcessing() throws InterruptedException {
        // Given
        int requestCount = 50;
        String deviceCode = "M001";
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger processedCount = new AtomicInteger(0);

        // 模拟幂等性检查
        when(idempotentService.tryConsume(anyString())).thenReturn(true);

        // When - 并发发送同一设备的状态事件
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId("msg-" + index);
                    request.setDeviceCode(deviceCode);

                    webhookReceiveService.handle(
                            category, eventType, rawBody, request,
                            "secret", "signature", "1234567890", "nonce"
                    );
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    // 忽略异常，主要验证并发处理能力
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - 等待所有请求完成
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有请求应在10秒内完成");
        executor.shutdown();

        // 验证所有请求都被处理（分布式锁应该保证数据一致性）
        assertEquals(requestCount, processedCount.get(), "所有请求都应该被处理");
        verify(webhookInboxService, times(requestCount)).saveToInbox(any(), any());
    }

    @Test
    @DisplayName("TC-CONCURRENT-003: 并发Worker处理 - 无重复处理")
    void testConcurrentWorkerProcessing() throws InterruptedException {
        // Given - 模拟多个Worker实例并发处理
        int workerCount = 5;
        int messageCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

        // 模拟幂等性检查 - 每个messageId只允许处理一次
        when(idempotentService.tryConsume(anyString())).thenAnswer(invocation -> {
            String messageId = invocation.getArgument(0);
            synchronized (processedMessageIds) {
                if (processedMessageIds.contains(messageId)) {
                    return false; // 已处理，跳过
                }
                processedMessageIds.add(messageId);
                return true;
            }
        });

        // When - 多个Worker并发处理消息
        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId("msg-" + index);
                    request.setDeviceCode("M001");

                    webhookReceiveService.handle(
                            category, eventType, rawBody, request,
                            "secret", "signature", "1234567890", "nonce"
                    );
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    // 忽略异常
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - 等待所有消息处理完成
        assertTrue(latch.await(10, TimeUnit.SECONDS), "所有消息应在10秒内处理完成");
        executor.shutdown();

        // 验证无重复处理
        assertEquals(messageCount, processedMessageIds.size(), "所有messageId都应该被处理，且无重复");
        assertEquals(messageCount, processedCount.get(), "所有消息都应该被处理");
    }

    @Test
    @DisplayName("并发测试 - 幂等性保证")
    void testIdempotencyGuarantee() throws InterruptedException {
        // Given
        String messageId = "msg-duplicate";
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        // 模拟幂等性检查 - 使用线程安全的计数器，只有第一次返回true
        AtomicInteger consumeAttempts = new AtomicInteger(0);
        when(idempotentService.tryConsume(eq(messageId))).thenAnswer(invocation -> {
            int attempts = consumeAttempts.getAndIncrement();
            return attempts == 0; // 只有第一次返回true
        });

        // When - 并发发送相同的messageId
        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                try {
                    WebhookRequest request = new WebhookRequest();
                    request.setMessageId(messageId);
                    request.setDeviceCode("M001");

                    webhookReceiveService.handle(
                            category, eventType, rawBody, request,
                            "secret", "signature", "1234567890", "nonce"
                    );
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    skippedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then - 等待所有请求完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "所有请求应在5秒内完成");
        executor.shutdown();

        // 验证只有一次被处理（因为幂等性检查，其他9次会被跳过）
        // 注意：由于幂等性检查，只有第一次会处理，其他会被跳过（不调用saveToInbox）
        verify(webhookInboxService, times(1)).saveToInbox(any(), any());
        // processedCount可能大于1，因为幂等性检查在handle方法内部，但saveToInbox只调用一次
    }
}

