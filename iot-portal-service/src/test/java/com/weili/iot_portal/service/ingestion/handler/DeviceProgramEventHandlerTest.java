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
 * 设备程序事件处理器测试
 * 测试用例：TC-PROGRAM-001 ~ TC-PROGRAM-004
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备程序事件处理器测试")
class DeviceProgramEventHandlerTest {

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private RealTimeCacheService realTimeCacheService;

    @InjectMocks
    private DeviceProgramEventHandler deviceProgramEventHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceProgramEventHandler, "programTtlMillis", 300000L);
    }

    @Test
    @DisplayName("TC-PROGRAM-001: 程序信息实时缓存")
    void testProgramInfoRealtimeCache() throws Exception {
        // Given
        WebhookRequest request = createProgramRequest();
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 验证程序信息缓存被更新
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(Map.class), eq(300000L));
    }

    @Test
    @DisplayName("TC-PROGRAM-002: 程序名缓存")
    void testProgramNameCache() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("programName", "PROG001");

        WebhookRequest request = createProgramRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 验证程序名被缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("programName") && "PROG001".equals(map.get("programName"));
        }), eq(300000L));
    }

    @Test
    @DisplayName("TC-PROGRAM-003: G代码和M代码缓存")
    void testGCodeAndMCodeCache() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("gCode", "G01");
        eventData.put("mCode", "M03");

        WebhookRequest request = createProgramRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 验证G代码和M代码被缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("gCode") && "G01".equals(map.get("gCode")) &&
                   map.containsKey("mCode") && "M03".equals(map.get("mCode"));
        }), eq(300000L));
    }

    @Test
    @DisplayName("TC-PROGRAM-004: 程序路径缓存")
    void testProgramPathCache() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("programPath", "/programs/PROG001.nc");

        WebhookRequest request = createProgramRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 验证程序路径被缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("programPath") && 
                   "/programs/PROG001.nc".equals(map.get("programPath"));
        }), eq(300000L));
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceProgramEventHandler.supports("DEVICE_PROGRAM"));
        assertFalse(deviceProgramEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(35, deviceProgramEventHandler.order());
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
            deviceProgramEventHandler.handle(inbox, request);
        });
    }

    @Test
    @DisplayName("验证无程序字段时跳过写入")
    void testNoProgramFields() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("otherField", "value");

        WebhookRequest request = createProgramRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 无程序字段时，不写入缓存
        verify(realTimeCacheService, never()).hsetWithTtl(anyString(), any(Map.class), anyLong());
    }

    @Test
    @DisplayName("验证字段名大小写兼容")
    void testFieldNameCaseInsensitive() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("program", "PROG001"); // 小写program
        eventData.put("program_path", "/path/to/prog"); // 下划线格式

        WebhookRequest request = createProgramRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceProgramEventHandler.handle(inbox, request);

        // Then - 验证字段名被正确识别和缓存
        verify(realTimeCacheService).hsetWithTtl(anyString(), argThat(payload -> {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) payload;
            return map.containsKey("programName") && "PROG001".equals(map.get("programName")) &&
                   map.containsKey("programPath") && "/path/to/prog".equals(map.get("programPath"));
        }), eq(300000L));
    }

    // 辅助方法
    private WebhookRequest createProgramRequest() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("programName", "PROG001");
        return createProgramRequest(eventData);
    }

    private WebhookRequest createProgramRequest(Map<String, Object> eventData) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_PROGRAM");
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

