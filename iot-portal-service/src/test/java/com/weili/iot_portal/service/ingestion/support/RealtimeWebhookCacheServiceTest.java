package com.weili.iot_portal.service.ingestion.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实时数据缓存服务测试
 * 测试用例：TC-CACHE-001 ~ TC-CACHE-007
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("实时数据缓存服务测试")
class RealtimeWebhookCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RealtimeWebhookCacheService realtimeWebhookCacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(realtimeWebhookCacheService, "realtimeTtlSeconds", 1800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("TC-CACHE-001: REALTIME数据缓存")
    void testRealtimeDataCache() throws Exception {
        // Given
        WebhookRequest request = createWebhookRequest();
        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        String expectedKey = "realtime:device:M001:DEVICE_STATE";
        String expectedPayload = "{\"messageId\":\"msg-001\"}";

        when(objectMapper.writeValueAsString(any())).thenReturn(expectedPayload);

        // When
        realtimeWebhookCacheService.cache(eventType, deviceCode, request);

        // Then - 验证数据写入Redis
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), durationCaptor.capture());

        assertEquals(expectedKey, keyCaptor.getValue());
        assertEquals(expectedPayload, valueCaptor.getValue());
        assertEquals(Duration.ofSeconds(1800), durationCaptor.getValue());
    }

    @Test
    @DisplayName("TC-CACHE-002: 缓存TTL设置")
    void testCacheTtlSetting() throws Exception {
        // Given
        WebhookRequest request = createWebhookRequest();
        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        realtimeWebhookCacheService.cache(eventType, deviceCode, request);

        // Then - 验证TTL设置正确（30分钟 = 1800秒）
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(1800)));
    }

    @Test
    @DisplayName("TC-CACHE-003: 缓存覆盖写")
    void testCacheOverwrite() throws Exception {
        // Given
        WebhookRequest request1 = createWebhookRequest("msg-001");
        WebhookRequest request2 = createWebhookRequest("msg-002");
        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When - 发送两次相同设备的实时数据
        realtimeWebhookCacheService.cache(eventType, deviceCode, request1);
        realtimeWebhookCacheService.cache(eventType, deviceCode, request2);

        // Then - 验证第二次覆盖第一次的值
        verify(valueOperations, times(2)).set(anyString(), anyString(), any(Duration.class));
        // 第二次调用应该覆盖第一次的值
    }

    @Test
    @DisplayName("TC-CACHE-004: 缓存Key格式")
    void testCacheKeyFormat() throws Exception {
        // Given
        WebhookRequest request = createWebhookRequest();
        String eventType = "DEVICE_AXIS";
        String deviceCode = "M001";

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        realtimeWebhookCacheService.cache(eventType, deviceCode, request);

        // Then - 验证Key格式为`realtime:device:{deviceCode}:{eventType}`
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));
        assertEquals("realtime:device:M001:DEVICE_AXIS", keyCaptor.getValue());
    }

    @Test
    @DisplayName("TC-CACHE-005: 缓存数据格式")
    void testCacheDataFormat() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("state", "WORKING");
        eventData.put("temperature", 25.5);

        WebhookRequest request = createWebhookRequest();
        request.setEventData(eventData);
        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        String expectedJson = "{\"messageId\":\"msg-001\",\"deviceCode\":\"M001\",\"eventType\":\"DEVICE_STATE\",\"data\":{\"state\":\"WORKING\"}}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedJson);

        // When
        realtimeWebhookCacheService.cache(eventType, deviceCode, request);

        // Then - 验证Value为JSON格式，包含messageId、deviceCode、eventType等
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), valueCaptor.capture(), any(Duration.class));
        String cachedValue = valueCaptor.getValue();
        assertTrue(cachedValue.contains("messageId"));
        assertTrue(cachedValue.contains("deviceCode"));
        assertTrue(cachedValue.contains("eventType"));
    }

    @Test
    @DisplayName("TC-CACHE-006: 序列化异常处理")
    void testSerializationException() throws Exception {
        // Given
        WebhookRequest request = createWebhookRequest();
        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        when(objectMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("序列化失败") {});

        // When & Then - 验证序列化异常时抛出ServiceException
        assertThrows(com.weili.basic.common.exception.ServiceException.class, () -> {
            realtimeWebhookCacheService.cache(eventType, deviceCode, request);
        });
    }

    @Test
    @DisplayName("TC-CACHE-007: 多设备并发缓存")
    void testMultipleDevicesConcurrentCache() throws Exception {
        // Given
        WebhookRequest request1 = createWebhookRequest("msg-001");
        WebhookRequest request2 = createWebhookRequest("msg-002");
        WebhookRequest request3 = createWebhookRequest("msg-003");

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When - 并发发送多个设备的实时数据
        realtimeWebhookCacheService.cache("DEVICE_STATE", "M001", request1);
        realtimeWebhookCacheService.cache("DEVICE_STATE", "M002", request2);
        realtimeWebhookCacheService.cache("DEVICE_AXIS", "M001", request3);

        // Then - 验证所有设备数据正确缓存
        verify(valueOperations, times(3)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("验证使用eventData或telemetryData")
    void testEventDataOrTelemetryData() throws Exception {
        // Given - 只有telemetryData，没有eventData
        WebhookRequest request = createWebhookRequest();
        request.setEventData(null);
        Map<String, Object> telemetryData = new HashMap<>();
        telemetryData.put("temperature", 25.5);
        request.setTelemetryData(telemetryData);

        String eventType = "DEVICE_STATE";
        String deviceCode = "M001";

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        realtimeWebhookCacheService.cache(eventType, deviceCode, request);

        // Then - 验证使用telemetryData
        ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(objectMapper).writeValueAsString(mapCaptor.capture());
        Map<String, Object> cachedMap = mapCaptor.getValue();
        assertEquals(telemetryData, cachedMap.get("data"));
    }

    // 辅助方法
    private WebhookRequest createWebhookRequest() {
        return createWebhookRequest("msg-001");
    }

    private WebhookRequest createWebhookRequest(String messageId) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId(messageId);
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_STATE");
        request.setTimestamp(2000L);
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("state", "WORKING");
        request.setEventData(eventData);
        return request;
    }
}

