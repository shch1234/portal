package com.weili.iot_portal.service.ingestion.support;

import com.weili.basic.redis.client.RedisClient;
import com.weili.iot_portal.common.constant.RedisConstant;
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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 未知设备告警服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("未知设备告警服务测试")
class UnknownDeviceAlertServiceTest {

    @Mock
    private RedisClient redisClient;

    @InjectMocks
    private UnknownDeviceAlertService unknownDeviceAlertService;

    @BeforeEach
    void setUp() {
        // 设置基础环境
    }

    @Test
    @DisplayName("记录未知设备告警")
    void testRecord() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = "M001";
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);

        verify(redisClient).set(
                keyCaptor.capture(),
                valueCaptor.capture(),
                ttlCaptor.capture(),
                unitCaptor.capture()
        );

        String key = keyCaptor.getValue();
        assertTrue(key.contains(tenantId));
        assertTrue(key.contains(deviceCode));
        assertEquals(TimeUnit.SECONDS, unitCaptor.getValue());
        assertTrue(ttlCaptor.getValue() > 0);
    }

    @Test
    @DisplayName("deviceCode为空时不记录")
    void testRecordWithEmptyDeviceCode() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = null;
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        verify(redisClient, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("deviceCode为空字符串时不记录")
    void testRecordWithBlankDeviceCode() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = "   ";
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        verify(redisClient, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("tenantId为空时使用unknown")
    void testRecordWithEmptyTenantId() {
        // Given
        String tenantId = null;
        String deviceCode = "M001";
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisClient).set(
                keyCaptor.capture(),
                anyString(),
                anyLong(),
                any(TimeUnit.class)
        );

        String key = keyCaptor.getValue();
        assertTrue(key.contains("unknown"));
    }

    @Test
    @DisplayName("source为空时使用unknown")
    void testRecordWithEmptySource() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = "M001";
        String tbDeviceId = "tb-device-001";
        String source = null;

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisClient).set(
                anyString(),
                valueCaptor.capture(),
                anyLong(),
                any(TimeUnit.class)
        );

        String value = valueCaptor.getValue();
        assertTrue(value.contains("unknown"));
    }

    @Test
    @DisplayName("验证Redis Key格式")
    void testRedisKeyFormat() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = "M001";
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisClient).set(
                keyCaptor.capture(),
                anyString(),
                anyLong(),
                any(TimeUnit.class)
        );

        String key = keyCaptor.getValue();
        // Key应该包含tenantId、deviceCode和timestamp
        assertTrue(key.contains(tenantId));
        assertTrue(key.contains(deviceCode));
        assertTrue(key.matches(".*\\d+.*")); // 包含时间戳（数字）
    }

    @Test
    @DisplayName("验证TTL为1天")
    void testTtlIsOneDay() {
        // Given
        String tenantId = "tenant-001";
        String deviceCode = "M001";
        String tbDeviceId = "tb-device-001";
        String source = "webhook";

        // When
        unknownDeviceAlertService.record(tenantId, deviceCode, tbDeviceId, source);

        // Then
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCaptor = ArgumentCaptor.forClass(TimeUnit.class);
        verify(redisClient).set(
                anyString(),
                anyString(),
                ttlCaptor.capture(),
                unitCaptor.capture()
        );

        // 1天 = 86400秒
        assertEquals(86400L, ttlCaptor.getValue());
        assertEquals(TimeUnit.SECONDS, unitCaptor.getValue());
    }
}

