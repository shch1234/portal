package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.common.exception.ServiceException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Webhook安全服务测试
 * 测试用例：TC-SECURITY-001 ~ TC-SECURITY-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook安全服务测试")
class WebhookSecurityServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private WebhookSecurityService webhookSecurityService;

    private static final String TOKEN = "test-webhook-token";
    private static final long TIMESTAMP_VALIDITY_MS = 300000L; // 5分钟
    private static final long NONCE_TTL_SECONDS = 300L; // 5分钟

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookSecurityService, "signatureToken", TOKEN);
        ReflectionTestUtils.setField(webhookSecurityService, "timestampValidityMs", TIMESTAMP_VALIDITY_MS);
        ReflectionTestUtils.setField(webhookSecurityService, "nonceTtlSeconds", NONCE_TTL_SECONDS);
        ReflectionTestUtils.setField(webhookSecurityService, "globalSecret", "");
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("TC-SECURITY-001: 签名生成正确性")
    void testSignatureGeneration() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-123";
        String messageBody = "{\"messageId\":\"test-001\"}";
        
        // When
        String signature = generateSignature(TOKEN, timestamp, nonce, messageBody);
        
        // Then
        assertNotNull(signature);
        assertEquals(64, signature.length()); // SHA-256 十六进制长度为64
        assertTrue(signature.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("TC-SECURITY-002: 签名验证正确性")
    void testSignatureVerificationSuccess() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-456";
        String messageBody = "{\"messageId\":\"test-002\"}";
        String signature = generateSignature(TOKEN, timestamp, nonce, messageBody);
        
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        
        // When & Then
        assertDoesNotThrow(() -> {
            webhookSecurityService.validateSignature(signature, timestamp, nonce, messageBody);
        });
        
        // 验证nonce被设置
        verify(valueOperations).setIfAbsent(anyString(), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("TC-SECURITY-003: 参数顺序不影响签名")
    void testSignatureParameterOrder() {
        // Given
        String timestamp = "1234567890";
        String nonce = "nonce123";
        String messageBody = "body";
        
        // 不同顺序的参数应该生成相同的签名（因为会排序）
        String signature1 = generateSignature(TOKEN, timestamp, nonce, messageBody);
        String signature2 = generateSignature(timestamp, TOKEN, nonce, messageBody);
        
        // Then - 排序后应该相同
        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("TC-SECURITY-004: 消息体变化导致签名变化")
    void testSignatureChangesWithMessageBody() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-789";
        String messageBody1 = "{\"messageId\":\"test-001\"}";
        String messageBody2 = "{\"messageId\":\"test-002\"}";
        
        String signature1 = generateSignature(TOKEN, timestamp, nonce, messageBody1);
        String signature2 = generateSignature(TOKEN, timestamp, nonce, messageBody2);
        
        // Then
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("TC-SECURITY-005: 时间戳变化导致签名变化")
    void testSignatureChangesWithTimestamp() {
        // Given
        String timestamp1 = "1234567890";
        String timestamp2 = "1234567891";
        String nonce = "test-nonce";
        String messageBody = "{\"messageId\":\"test\"}";
        
        String signature1 = generateSignature(TOKEN, timestamp1, nonce, messageBody);
        String signature2 = generateSignature(TOKEN, timestamp2, nonce, messageBody);
        
        // Then
        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("TC-WEBHOOK-008: 签名验证失败")
    void testSignatureVerificationFailure() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-fail";
        String messageBody = "{\"messageId\":\"test\"}";
        String wrongSignature = "wrong-signature-1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature(wrongSignature, timestamp, nonce, messageBody);
        });
        
        assertTrue(exception.getMessage().contains("签名验证失败"));
    }

    @Test
    @DisplayName("TC-WEBHOOK-009: 时间戳验证失败")
    void testTimestampValidationFailure() {
        // Given
        long expiredTimestamp = System.currentTimeMillis() - TIMESTAMP_VALIDITY_MS - 1000; // 过期1秒
        String timestamp = String.valueOf(expiredTimestamp);
        String nonce = "test-nonce-expired";
        String messageBody = "{\"messageId\":\"test\"}";
        String signature = generateSignature(TOKEN, timestamp, nonce, messageBody);
        
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature(signature, timestamp, nonce, messageBody);
        });
        
        assertTrue(exception.getMessage().contains("请求已过期"));
    }

    @Test
    @DisplayName("TC-WEBHOOK-010: nonce重复请求")
    void testNonceReplayAttack() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce-replay";
        String messageBody = "{\"messageId\":\"test\"}";
        String signature = generateSignature(TOKEN, timestamp, nonce, messageBody);
        
        // 第一次请求成功
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        
        assertDoesNotThrow(() -> {
            webhookSecurityService.validateSignature(signature, timestamp, nonce, messageBody);
        });
        
        // 第二次请求失败（nonce已存在）
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        
        // When & Then
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature(signature, timestamp, nonce, messageBody);
        });
        
        assertTrue(exception.getMessage().contains("重复请求"));
    }

    @Test
    @DisplayName("TC-WEBHOOK-011: 缺少签名参数")
    void testMissingSignatureParameters() {
        // Given
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "test-nonce";
        String messageBody = "{\"messageId\":\"test\"}";
        
        // When & Then - 缺少signature
        ServiceException exception1 = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature(null, timestamp, nonce, messageBody);
        });
        assertTrue(exception1.getMessage().contains("签名参数缺失"));
        
        // When & Then - 缺少timestamp
        ServiceException exception2 = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature("signature", null, nonce, messageBody);
        });
        assertTrue(exception2.getMessage().contains("签名参数缺失"));
        
        // When & Then - 缺少nonce
        ServiceException exception3 = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validateSignature("signature", timestamp, null, messageBody);
        });
        assertTrue(exception3.getMessage().contains("签名参数缺失"));
    }

    @Test
    @DisplayName("验证Header密钥")
    void testHeaderSecretValidation() {
        // Given
        ReflectionTestUtils.setField(webhookSecurityService, "globalSecret", "secret-key");
        
        // When & Then - 正确的密钥
        assertDoesNotThrow(() -> {
            webhookSecurityService.validate("secret-key");
        });
        
        // When & Then - 错误的密钥
        ServiceException exception = assertThrows(ServiceException.class, () -> {
            webhookSecurityService.validate("wrong-secret");
        });
        assertTrue(exception.getMessage().contains("密钥验证失败"));
    }

    /**
     * 生成签名的辅助方法（与实现保持一致）
     */
    private String generateSignature(String token, String timestamp, String nonce, String messageBody) {
        String[] arr = {token, timestamp, nonce, messageBody == null ? "" : messageBody};
        java.util.Arrays.sort(arr);
        String joined = String.join("", arr);
        return sha256(joined);
    }

    private String sha256(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

