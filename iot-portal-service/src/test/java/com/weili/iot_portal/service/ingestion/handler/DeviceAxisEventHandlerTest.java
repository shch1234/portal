package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备轴坐标事件处理器测试
 * 测试用例：TC-AXIS-001 ~ TC-AXIS-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备轴坐标事件处理器测试")
class DeviceAxisEventHandlerTest {

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private RealTimeCacheService realTimeCacheService;

    @InjectMocks
    private DeviceAxisEventHandler deviceAxisEventHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceAxisEventHandler, "axisTtlMillis", 300000L);
        ReflectionTestUtils.setField(deviceAxisEventHandler, "axisCurveTtlMillis", 600000L);
        ReflectionTestUtils.setField(deviceAxisEventHandler, "axisCurveMaxLenLoad", 2000);
        ReflectionTestUtils.setField(deviceAxisEventHandler, "axisCurveMaxLenRpm", 2000);
        ReflectionTestUtils.setField(deviceAxisEventHandler, "axisCurveMaxLenFeed", 2000);
    }

    @Test
    @DisplayName("TC-AXIS-001: 轴坐标实时缓存")
    void testAxisRealtimeCache() throws Exception {
        // Given
        WebhookRequest request = createAxisRequest();
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 验证轴坐标缓存被更新
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(Map.class), eq(300000L));
    }

    @Test
    @DisplayName("TC-AXIS-002: 多轴坐标处理")
    void testMultipleAxisProcessing() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("axis.X.absolute", 100.5);
        eventData.put("axis.Y.absolute", 200.3);
        eventData.put("axis.Z.absolute", 300.7);
        eventData.put("axis.A.absolute", 45.0);
        eventData.put("axis.B.absolute", 90.0);
        eventData.put("axis.C.absolute", 180.0);

        WebhookRequest request = createAxisRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 验证所有轴坐标都被缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("axis.X.absolute") &&
                   map.containsKey("axis.Y.absolute") &&
                   map.containsKey("axis.Z.absolute") &&
                   map.containsKey("axis.A.absolute") &&
                   map.containsKey("axis.B.absolute") &&
                   map.containsKey("axis.C.absolute");
        }), eq(300000L));
    }

    @Test
    @DisplayName("TC-AXIS-003: 轴坐标TTL")
    void testAxisTtl() throws Exception {
        // Given
        WebhookRequest request = createAxisRequest();
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 验证TTL设置正确（300秒 = 300000毫秒）
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(Map.class), eq(300000L));
    }

    @Test
    @DisplayName("TC-AXIS-004: 倍率值处理")
    void testRatioValue() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("axis.X.absolute", 100.0);
        eventData.put("ratio", 1.5);

        WebhookRequest request = createAxisRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 验证倍率值被缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("ratio") && "1.5".equals(map.get("ratio"));
        }), eq(300000L));
    }

    @Test
    @DisplayName("TC-AXIS-005: 曲线数据缓存")
    void testCurveDataCache() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("axis.X.absolute", 100.0);
        eventData.put("load", 50.5);
        eventData.put("rpm", 1500);
        eventData.put("feed", 200.3);

        WebhookRequest request = createAxisRequest(eventData);
        request.setDataTimestamp(2000L);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 验证曲线数据被缓存（load、rpm、feed）
        verify(realTimeCacheService, times(3)).lpushTrimExpire(
                anyString(), anyString(), anyInt(), eq(600000L));
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceAxisEventHandler.supports("DEVICE_AXIS"));
        assertFalse(deviceAxisEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(20, deviceAxisEventHandler.order());
    }

    @Test
    @DisplayName("验证事件数据为空时抛出异常")
    void testEmptyEventData() {
        // Given
        WebhookRequest request = new WebhookRequest();
        request.setEventData(new HashMap<>());
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When & Then
        assertThrows(Exception.class, () -> {
            deviceAxisEventHandler.handle(inbox, request);
        });
    }

    @Test
    @DisplayName("验证无axis字段时跳过写入")
    void testNoAxisFields() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("otherField", "value");

        WebhookRequest request = createAxisRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceAxisEventHandler.handle(inbox, request);

        // Then - 无axis字段时，不写入缓存
        verify(realTimeCacheService, never()).hsetWithTtl(anyString(), any(Map.class), anyLong());
    }

    // 辅助方法
    private WebhookRequest createAxisRequest() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("axis.X.absolute", 100.0);
        eventData.put("axis.Y.absolute", 200.0);
        return createAxisRequest(eventData);
    }

    private WebhookRequest createAxisRequest(Map<String, Object> eventData) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_AXIS");
        request.setDataTimestamp(2000L);
        request.setEventData(eventData);
        return request;
    }

    private WebhookInboxDO createInbox() {
        WebhookInboxDO inbox = new WebhookInboxDO();
        inbox.setMessageId("msg-001");
        inbox.setStatus("PENDING");
        return inbox;
    }

    private DeviceIdentityCacheService.DeviceIdentity createIdentity() {
        return new DeviceIdentityCacheService.DeviceIdentity(
                "device-info-001",
                "factory-001"
        );
    }
}

