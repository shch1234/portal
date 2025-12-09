package com.weili.iot_portal.service.ingestion.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook幂等性服务测试
 * 测试用例：TC-IDEMPOTENT-001 ~ TC-IDEMPOTENT-006
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook幂等性服务测试")
class WebhookIdempotentServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private WebhookIdempotentService webhookIdempotentService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-001: 首次处理消息")
    void testFirstTimeConsume() {
        // Given
        String messageId = "test-message-001";
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);
        
        // When
        boolean result = webhookIdempotentService.tryConsume(messageId);
        
        // Then
        assertTrue(result);
        verify(valueOperations).setIfAbsent(
                eq("webhook:idempotent:" + messageId),
                eq("1"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-002: 重复消息处理")
    void testDuplicateMessageConsume() {
        // Given
        String messageId = "test-message-002";
        
        // 第一次处理成功
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);
        
        boolean firstResult = webhookIdempotentService.tryConsume(messageId);
        assertTrue(firstResult);
        
        // 第二次处理失败（已存在）
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(false);
        
        // When
        boolean secondResult = webhookIdempotentService.tryConsume(messageId);
        
        // Then
        assertFalse(secondResult);
        verify(valueOperations, times(2)).setIfAbsent(
                eq("webhook:idempotent:" + messageId),
                eq("1"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-003: messageId为空")
    void testEmptyMessageId() {
        // Given
        String messageId = null;
        
        // When
        boolean result1 = webhookIdempotentService.tryConsume(messageId);
        
        // Then - 空messageId应该允许处理（兼容性）
        assertTrue(result1);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
    
    @Test
    @DisplayName("TC-IDEMPOTENT-003-2: messageId为空字符串")
    void testEmptyStringMessageId() {
        // Given - 空字符串
        String emptyMessageId = "";
        
        // When
        boolean result = webhookIdempotentService.tryConsume(emptyMessageId);
        
        // Then
        assertTrue(result);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-004: Redis TTL过期")
    void testRedisTtlExpiration() {
        // Given
        String messageId = "test-message-004";
        
        // 模拟TTL已过期，key不存在，可以重新处理
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);
        
        // When
        boolean result = webhookIdempotentService.tryConsume(messageId);
        
        // Then
        assertTrue(result);
        verify(valueOperations).setIfAbsent(
                eq("webhook:idempotent:" + messageId),
                eq("1"),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-005: 并发重复请求")
    void testConcurrentDuplicateRequests() {
        // Given
        String messageId = "test-message-005";
        
        // 模拟并发场景：第一个请求成功，其他请求失败
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true)  // 第一次成功
                .thenReturn(false) // 后续失败
                .thenReturn(false);
        
        // When
        boolean result1 = webhookIdempotentService.tryConsume(messageId);
        boolean result2 = webhookIdempotentService.tryConsume(messageId);
        boolean result3 = webhookIdempotentService.tryConsume(messageId);
        
        // Then
        assertTrue(result1, "第一个请求应该成功");
        assertFalse(result2, "第二个请求应该被幂等拦截");
        assertFalse(result3, "第三个请求应该被幂等拦截");
    }

    @Test
    @DisplayName("TC-IDEMPOTENT-006: Redis异常时幂等性")
    void testRedisExceptionHandling() {
        // Given
        String messageId = "test-message-006";
        
        // 模拟Redis异常
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        // When & Then - 应该抛出异常，但实际实现中可能需要降级处理
        assertThrows(RuntimeException.class, () -> {
            webhookIdempotentService.tryConsume(messageId);
        });
    }

    @Test
    @DisplayName("验证TTL设置")
    void testTtlSetting() {
        // Given
        String messageId = "test-message-ttl";
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);
        
        // When
        webhookIdempotentService.tryConsume(messageId);
        
        // Then - 验证TTL为24小时
        verify(valueOperations).setIfAbsent(
                anyString(),
                eq("1"),
                eq(Duration.ofHours(24))
        );
    }
}

