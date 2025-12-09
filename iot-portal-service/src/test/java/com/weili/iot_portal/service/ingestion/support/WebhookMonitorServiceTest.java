package com.weili.iot_portal.service.ingestion.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Webhook监控服务测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Webhook监控服务测试")
class WebhookMonitorServiceTest {

    @Test
    @DisplayName("记录匹配的Handler - 无MeterRegistry")
    void testRecordMatchedWithoutMeterRegistry() {
        // Given - MeterRegistry为null
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordMatched("DEVICE_STATE", "DeviceStateEventHandler"));
    }

    @Test
    @DisplayName("记录未匹配的Handler - 无MeterRegistry")
    void testRecordUnmatchedWithoutMeterRegistry() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordUnmatched("UNKNOWN_EVENT"));
    }

    @Test
    @DisplayName("记录成功处理 - 无MeterRegistry")
    void testRecordSuccessWithoutMeterRegistry() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordSuccess("DEVICE_STATE", 150L));
    }

    @Test
    @DisplayName("记录失败处理 - 无MeterRegistry")
    void testRecordFailureWithoutMeterRegistry() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordFailure("DEVICE_STATE", "处理失败", 200L, true));
    }

    @Test
    @DisplayName("处理null值 - eventType为null")
    void testNullEventType() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> {
            service.recordMatched(null, "Handler");
            service.recordUnmatched(null);
            service.recordSuccess(null, 100L);
            service.recordFailure(null, "error", 100L, false);
        });
    }

    @Test
    @DisplayName("处理null值 - handlerName为null")
    void testNullHandlerName() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordMatched("DEVICE_STATE", null));
    }

    @Test
    @DisplayName("处理空字符串值")
    void testEmptyStringValues() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> {
            service.recordMatched("", "");
            service.recordUnmatched("");
            service.recordSuccess("", 100L);
            service.recordFailure("", "", 100L, false);
        });
    }

    @Test
    @DisplayName("记录失败处理 - 不重试")
    void testRecordFailureNoRetry() {
        // Given
        WebhookMonitorService service = new WebhookMonitorService();

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> service.recordFailure("DEVICE_STATE", "处理失败", 200L, false));
    }
}
