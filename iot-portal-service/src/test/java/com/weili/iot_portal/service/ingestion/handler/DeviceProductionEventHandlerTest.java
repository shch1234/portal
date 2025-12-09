package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备产量事件处理器测试
 * 测试用例：TC-PRODUCTION-001 ~ TC-PRODUCTION-007
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备产量事件处理器测试")
class DeviceProductionEventHandlerTest {

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private DeviceProductionRecordRepository deviceProductionRecordRepository;

    @Mock
    private ShiftConfigurationService shiftConfigurationService;

    @InjectMocks
    private DeviceProductionEventHandler deviceProductionEventHandler;

    @BeforeEach
    void setUp() {
        // 设置默认的班次配置
        ShiftTimeRange shiftRange = new ShiftTimeRange();
        shiftRange.setStartTs(System.currentTimeMillis() - 3600000); // 1小时前
        shiftRange.setEndTs(System.currentTimeMillis() + 3600000); // 1小时后
        shiftRange.setShiftCode("DAY");
        
        lenient().when(shiftConfigurationService.calculateShiftRange(any(), any(), any(), anyLong()))
                .thenReturn(shiftRange);
    }

    @Test
    @DisplayName("TC-PRODUCTION-001: 产量开始事件")
    void testProductionStart() throws Exception {
        // Given
        WebhookRequest request = createProductionRequest("start", 2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceProductionRecordDO> captor = ArgumentCaptor.forClass(DeviceProductionRecordDO.class);
        verify(deviceProductionRecordRepository).insert(captor.capture());
        
        DeviceProductionRecordDO saved = captor.getValue();
        assertEquals(2000L, saved.getStartTs());
        assertNull(saved.getEndTs());
        assertNotNull(saved.getShiftDate());
        assertNotNull(saved.getShiftCode());
    }

    @Test
    @DisplayName("TC-PRODUCTION-002: 产量结束事件（有进行中记录）")
    void testProductionEndWithOngoing() throws Exception {
        // Given
        WebhookRequest request = createProductionRequest("end", 3000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceProductionRecordDO ongoing = createProductionRecord(2000L, null);
        when(deviceProductionRecordRepository.findLatestOngoing(any(), any()))
                .thenReturn(Optional.of(ongoing));
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then
        verify(deviceProductionRecordRepository).updateById(ongoing);
        assertEquals(3000L, ongoing.getEndTs());
        assertEquals(1000, ongoing.getDurationS());
    }

    @Test
    @DisplayName("TC-PRODUCTION-003: 产量结束事件（无进行中记录）")
    void testProductionEndWithoutOngoing() throws Exception {
        // Given
        WebhookRequest request = createProductionRequest("end", 3000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        when(deviceProductionRecordRepository.findLatestOngoing(any(), any()))
                .thenReturn(Optional.empty());
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceProductionRecordDO> captor = ArgumentCaptor.forClass(DeviceProductionRecordDO.class);
        verify(deviceProductionRecordRepository).insert(captor.capture());
        
        DeviceProductionRecordDO saved = captor.getValue();
        assertEquals(3000L, saved.getStartTs());
        assertEquals(3000L, saved.getEndTs());
        assertEquals(0, saved.getDurationS());
    }

    @Test
    @DisplayName("TC-PRODUCTION-004: 班次信息计算")
    void testShiftInfoCalculation() throws Exception {
        // Given
        WebhookRequest request = createProductionRequest("start", 2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        ShiftTimeRange shiftRange = new ShiftTimeRange();
        shiftRange.setStartTs(1000L);
        shiftRange.setEndTs(2000L);
        shiftRange.setShiftCode("NIGHT");
        when(shiftConfigurationService.calculateShiftRange(any(), any(), any(), anyLong()))
                .thenReturn(shiftRange);
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceProductionRecordDO> captor = ArgumentCaptor.forClass(DeviceProductionRecordDO.class);
        verify(deviceProductionRecordRepository).insert(captor.capture());
        
        DeviceProductionRecordDO saved = captor.getValue();
        assertEquals("NIGHT", saved.getShiftCode());
        assertNotNull(saved.getShiftDate());
    }

    @Test
    @DisplayName("TC-PRODUCTION-006: 产量事件数据完整性")
    void testProductionDataIntegrity() throws Exception {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", "start");
        eventData.put("programName", "PROG001");
        eventData.put("countSource", "MANUAL");
        
        WebhookRequest request = createProductionRequest(eventData, 2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceProductionRecordDO> captor = ArgumentCaptor.forClass(DeviceProductionRecordDO.class);
        verify(deviceProductionRecordRepository).insert(captor.capture());
        
        DeviceProductionRecordDO saved = captor.getValue();
        assertEquals("PROG001", saved.getProgramName());
        assertEquals("MANUAL", saved.getCountSource());
    }

    @Test
    @DisplayName("TC-PRODUCTION-007: 时间戳处理")
    void testTimestampHandling() throws Exception {
        // Given
        WebhookRequest request = createProductionRequest("start", null);
        request.setDataTimestamp(2000L);
        request.setTimestamp(3000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        // When
        deviceProductionEventHandler.handle(inbox, request);
        
        // Then - 应该优先使用dataTimestamp
        ArgumentCaptor<DeviceProductionRecordDO> captor = ArgumentCaptor.forClass(DeviceProductionRecordDO.class);
        verify(deviceProductionRecordRepository).insert(captor.capture());
        
        DeviceProductionRecordDO saved = captor.getValue();
        assertEquals(2000L, saved.getStartTs());
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceProductionEventHandler.supports("DEVICE_PRODUCTION"));
        assertFalse(deviceProductionEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(25, deviceProductionEventHandler.order());
    }

    @Test
    @DisplayName("验证status必须为start或end")
    void testInvalidStatus() {
        // Given
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", "invalid");
        
        WebhookRequest request = createProductionRequest(eventData, 2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        // When & Then
        assertThrows(Exception.class, () -> {
            deviceProductionEventHandler.handle(inbox, request);
        });
    }

    // 辅助方法
    private WebhookRequest createProductionRequest(String status, Long timestamp) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", status);
        return createProductionRequest(eventData, timestamp);
    }

    private WebhookRequest createProductionRequest(Map<String, Object> eventData, Long timestamp) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_PRODUCTION");
        request.setDataTimestamp(timestamp);
        request.setTimestamp(timestamp != null ? timestamp : System.currentTimeMillis() / 1000);
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

    private DeviceProductionRecordDO createProductionRecord(Long startTs, Long endTs) {
        DeviceProductionRecordDO record = new DeviceProductionRecordDO();
        record.setStartTs(startTs);
        record.setEndTs(endTs);
        record.setTenantUuid("tenant-001");
        record.setDeviceInfoId("device-info-001");
        return record;
    }
}

