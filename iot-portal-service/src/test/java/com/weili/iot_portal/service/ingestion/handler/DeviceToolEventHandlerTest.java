package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolCompensationRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备刀具事件处理器测试
 * 测试用例：TC-TOOL-001 ~ TC-TOOL-004
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备刀具事件处理器测试")
class DeviceToolEventHandlerTest {

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private RealTimeCacheService realTimeCacheService;

    @Mock
    private ToolCompensationRepository toolCompensationRepository;

    @InjectMocks
    private DeviceToolEventHandler deviceToolEventHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceToolEventHandler, "toolTtlMillis", 300000L);
    }

    @Test
    @DisplayName("TC-TOOL-001: 刀具信息实时缓存")
    void testToolInfoRealtimeCache() throws Exception {
        // Given
        WebhookRequest request = createToolRequest();
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(toolCompensationRepository.findActive(any(), any(), any()))
                .thenReturn(null);

        // When
        deviceToolEventHandler.handle(inbox, request);

        // Then - 验证刀具信息缓存被更新
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(Map.class), eq(300000L));
    }

    @Test
    @DisplayName("TC-TOOL-002: 刀具更换事件（刀补补偿处理）")
    void testToolChangeEvent() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolNumber", "T01");
        eventData.put("holderNumber", "H01");
        eventData.put("offsetX", 10.5);
        eventData.put("offsetY", 20.3);
        eventData.put("offsetZ", 30.7);

        WebhookRequest request = createToolRequest(eventData);
        request.setDataTimestamp(2000L);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(toolCompensationRepository.findActive(any(), any(), any()))
                .thenReturn(null);

        // When
        deviceToolEventHandler.handle(inbox, request);

        // Then - 验证刀补补偿记录被创建
        ArgumentCaptor<ToolCompensationDO> captor = ArgumentCaptor.forClass(ToolCompensationDO.class);
        verify(toolCompensationRepository).insert(captor.capture());

        ToolCompensationDO saved = captor.getValue();
        assertEquals("H01", saved.getToolHolderNo());
        assertEquals(1, saved.getActive());
        assertNotNull(saved.getCompValueJson());
    }

    @Test
    @DisplayName("TC-TOOL-003: 刀补补偿版本化")
    void testToolCompensationVersioning() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolNumber", "T01");
        eventData.put("holderNumber", "H01");
        eventData.put("offsetX", 10.5);

        WebhookRequest request = createToolRequest(eventData);
        request.setDataTimestamp(2000L);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // 存在活跃的刀补记录
        ToolCompensationDO existing = new ToolCompensationDO();
        existing.setId(1L);
        existing.setToolHolderNo("H01");
        existing.setVersion(1);
        existing.setStartTs(1000L);
        existing.setActive(1);
        Map<String, Object> oldCompValue = new HashMap<>();
        oldCompValue.put("offsetX", 5.0);
        existing.setCompValueJson(oldCompValue);

        when(toolCompensationRepository.findActive(any(), any(), any()))
                .thenReturn(existing);

        // When
        deviceToolEventHandler.handle(inbox, request);

        // Then - 验证旧记录被关闭，新记录被创建
        verify(toolCompensationRepository).updateById(existing);
        assertEquals(0, existing.getActive());
        assertEquals(2000L, existing.getEndTs());

        ArgumentCaptor<ToolCompensationDO> captor = ArgumentCaptor.forClass(ToolCompensationDO.class);
        verify(toolCompensationRepository).insert(captor.capture());
        ToolCompensationDO newRecord = captor.getValue();
        assertEquals(2, newRecord.getVersion()); // 版本号递增
        assertEquals(1, newRecord.getActive());
    }

    @Test
    @DisplayName("TC-TOOL-004: 刀补补偿重复处理（相同值跳过）")
    void testToolCompensationDuplicateSkip() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolNumber", "T01");
        eventData.put("holderNumber", "H01");
        eventData.put("offsetX", 10.5);

        WebhookRequest request = createToolRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // 存在活跃的刀补记录，且补偿值相同
        // 注意：extractCompensationValue会提取所有offset/comp/tool/holder开头的字段
        // 所以compValueJson需要包含这些字段
        ToolCompensationDO existing = new ToolCompensationDO();
        existing.setToolHolderNo("H01");
        existing.setVersion(1);
        existing.setActive(1);
        Map<String, Object> sameCompValue = new HashMap<>();
        sameCompValue.put("offsetX", 10.5);
        sameCompValue.put("toolNumber", "T01");
        sameCompValue.put("holderNumber", "H01");
        existing.setCompValueJson(sameCompValue);

        when(toolCompensationRepository.findActive(any(), any(), any()))
                .thenReturn(existing);

        // When
        deviceToolEventHandler.handle(inbox, request);

        // Then - 验证相同值时跳过，不创建新记录
        verify(toolCompensationRepository, never()).updateById(any());
        verify(toolCompensationRepository, never()).insert(any());
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceToolEventHandler.supports("DEVICE_TOOL"));
        assertFalse(deviceToolEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(30, deviceToolEventHandler.order());
    }

    @Test
    @DisplayName("验证缺少holderNumber时跳过刀补入库")
    void testMissingHolderNumber() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolNumber", "T01");
        // 缺少holderNumber

        WebhookRequest request = createToolRequest(eventData);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // When
        deviceToolEventHandler.handle(inbox, request);

        // Then - 验证缓存被更新，但刀补记录不被创建
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(Map.class), anyLong());
        verify(toolCompensationRepository, never()).insert(any());
    }

    // 辅助方法
    private WebhookRequest createToolRequest() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("toolNumber", "T01");
        eventData.put("holderNumber", "H01");
        return createToolRequest(eventData);
    }

    private WebhookRequest createToolRequest(Map<String, Object> eventData) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_TOOL");
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

