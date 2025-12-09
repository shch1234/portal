package com.weili.iot_portal.service.ingestion.handler;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolUsageHistoryDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.ToolUsageHistoryRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 刀具换刀事件处理器测试
 * 覆盖 TC-TOOL-CHANGE-001 ~ TC-TOOL-CHANGE-005
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("刀具换刀事件处理器测试")
class DeviceToolChangeEventHandlerTest {

    @Mock
    private ToolUsageHistoryRepository toolUsageHistoryRepository;

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private DeviceToolChangeEventHandler deviceToolChangeEventHandler;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("TC-TOOL-CHANGE-001: 正常换刀，关闭旧记录并插入新记录")
    void testNormalToolChange() throws Exception {
        // Given
        WebhookRequest request = createRequest("T01", "T02");
        request.setDataTimestamp(2_000_000L); // 毫秒，换算为秒=2000
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);

        // 模拟获取锁成功
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // 模拟存在进行中的旧刀记录（endTs为null表示进行中）
        ToolUsageHistoryDO latest = new ToolUsageHistoryDO();
        latest.setId("old");
        latest.setToolNo("T01");
        latest.setStartTs(1000L);
        latest.setEndTs(null); // 进行中
        when(toolUsageHistoryRepository.findLatestOngoing(any(), any()))
                .thenReturn(latest);

        // When
        deviceToolChangeEventHandler.handle(inbox, request);

        // Then - 旧记录被关闭，新记录被插入
        verify(toolUsageHistoryRepository).updateById(latest);
        assertEquals(2000L, latest.getEndTs());
        assertEquals(1000, latest.getDurationS());

        ArgumentCaptor<ToolUsageHistoryDO> captor = ArgumentCaptor.forClass(ToolUsageHistoryDO.class);
        verify(toolUsageHistoryRepository).insert(captor.capture());
        ToolUsageHistoryDO saved = captor.getValue();
        assertEquals("T02", saved.getToolNo());
        assertEquals("holder-01", saved.getToolMagazineNo());
        assertEquals(2000L, saved.getStartTs());
        assertNull(saved.getEndTs()); // 新记录进行中
        // 验证锁被释放
        verify(redisTemplate).delete(startsWith("device_tool_lock:"));
    }

    @Test
    @DisplayName("TC-TOOL-CHANGE-002: 获取锁失败抛出异常")
    void testLockAcquireFail() {
        // Given
        WebhookRequest request = createRequest("T01", "T02");
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false); // 获取锁失败

        // When & Then
        assertThrows(ServiceException.class, () -> deviceToolChangeEventHandler.handle(inbox, request));
    }

    @Test
    @DisplayName("TC-TOOL-CHANGE-003: 缺少currentToolNo抛出异常")
    void testMissingCurrentToolNo() {
        // Given
        WebhookRequest request = createRequest("T01", null);
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // When & Then
        assertThrows(ServiceException.class, () -> deviceToolChangeEventHandler.handle(inbox, request));
    }

    @Test
    @DisplayName("TC-TOOL-CHANGE-004: 无进行中记录，直接插入新记录")
    void testNoOngoingRecord() throws Exception {
        // Given
        WebhookRequest request = createRequest(null, "T02");
        request.setDataTimestamp(1_000_000L); // 秒=1000
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(toolUsageHistoryRepository.findLatestOngoing(any(), any()))
                .thenReturn(null);

        // When
        deviceToolChangeEventHandler.handle(inbox, request);

        // Then - 仅插入新记录
        verify(toolUsageHistoryRepository, never()).updateById(any());
        ArgumentCaptor<ToolUsageHistoryDO> captor = ArgumentCaptor.forClass(ToolUsageHistoryDO.class);
        verify(toolUsageHistoryRepository).insert(captor.capture());
        assertEquals("T02", captor.getValue().getToolNo());
        assertEquals(1000L, captor.getValue().getStartTs());
        assertNull(captor.getValue().getEndTs());
    }

    @Test
    @DisplayName("TC-TOOL-CHANGE-005: previousToolNo不匹配也会关闭旧记录")
    void testPreviousToolNoMismatch() throws Exception {
        // Given
        WebhookRequest request = createRequest("TXX", "T02");
        request.setDataTimestamp(1_500_000L); // 秒=1500
        WebhookInboxDO inbox = createInbox();

        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        ToolUsageHistoryDO latest = new ToolUsageHistoryDO();
        latest.setId("old");
        latest.setToolNo("T01");
        latest.setStartTs(1000L);
        latest.setEndTs(null); // 进行中
        when(toolUsageHistoryRepository.findLatestOngoing(any(), any()))
                .thenReturn(latest);

        // When
        deviceToolChangeEventHandler.handle(inbox, request);

        // Then - 旧记录被关闭，新记录插入
        verify(toolUsageHistoryRepository).updateById(latest);
        assertEquals(1500L, latest.getEndTs());
        verify(toolUsageHistoryRepository).insert(any(ToolUsageHistoryDO.class));
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceToolChangeEventHandler.supports("DEVICE_TOOL_CHANGE"));
        assertFalse(deviceToolChangeEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(25, deviceToolChangeEventHandler.order());
    }

    // 辅助方法
    private WebhookRequest createRequest(String previous, String current) {
        Map<String, Object> eventData = new HashMap<>();
        if (previous != null) {
            eventData.put("previousToolNo", previous);
        }
        if (current != null) {
            eventData.put("currentToolNo", current);
        }
        eventData.put("toolHolderNumber", "holder-01");
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_TOOL_CHANGE");
        request.setTimestamp(2_000_000L);
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

