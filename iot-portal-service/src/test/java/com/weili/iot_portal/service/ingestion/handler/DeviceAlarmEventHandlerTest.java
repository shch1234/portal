package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceAlarmHistoryRepository;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备报警事件处理器测试
 * 测试用例：TC-ALARM-001 ~ TC-ALARM-008
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备报警事件处理器测试")
class DeviceAlarmEventHandlerTest {

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;

    @InjectMocks
    private DeviceAlarmEventHandler deviceAlarmEventHandler;

    @Test
    @DisplayName("TC-ALARM-001: 新增报警")
    void testNewAlarm() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        Map<String, Object> alarm = new HashMap<>();
        alarm.put("alarmCode", "ALARM001");
        alarm.put("alarmText", "温度过高");
        alarm.put("alarmLevel", "ERROR");
        alarms.add(alarm);
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceAlarmHistoryDO> captor = ArgumentCaptor.forClass(DeviceAlarmHistoryDO.class);
        verify(deviceAlarmHistoryRepository).insert(captor.capture());
        
        DeviceAlarmHistoryDO saved = captor.getValue();
        assertEquals("ALARM001", saved.getAlarmCode());
        assertEquals("温度过高", saved.getAlarmText());
        assertEquals("ERROR", saved.getAlarmLevel());
        assertEquals(1, saved.getIsActive());
        assertNull(saved.getEndTs());
    }

    @Test
    @DisplayName("TC-ALARM-002: 更新现有报警")
    void testUpdateExistingAlarm() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        Map<String, Object> alarm = new HashMap<>();
        alarm.put("alarmCode", "ALARM001");
        alarm.put("alarmText", "温度过高（更新）");
        alarm.put("alarmLevel", "WARNING");
        alarms.add(alarm);
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceAlarmHistoryDO existing = createAlarmHistory("ALARM001", "温度过高", "ERROR");
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.singletonList(existing));
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then
        verify(deviceAlarmHistoryRepository).updateById(existing);
        assertEquals("温度过高（更新）", existing.getAlarmText());
        assertEquals("WARNING", existing.getAlarmLevel());
    }

    @Test
    @DisplayName("TC-ALARM-003: 报警结束（空数组表示所有报警结束）")
    void testAlarmEnd() throws Exception {
        // Given - 正常的业务逻辑：如果t1时刻有三条报警，t2时刻没有报警（空数组），
        // 那么就认为所有报警已经结束了，应该关闭所有活跃报警
        List<Map<String, Object>> alarms = new ArrayList<>(); // 空数组表示没有报警
        
        WebhookRequest request = createAlarmRequest(alarms);
        request.setDataTimestamp(2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceAlarmHistoryDO activeAlarm = createAlarmHistory("ALARM001", "温度过高", "ERROR");
        activeAlarm.setStartTs(1000L);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.singletonList(activeAlarm));
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then - 空数组时，incomingCodes为空，所有活跃报警都应该被关闭
        verify(deviceAlarmHistoryRepository).updateById(activeAlarm);
        assertEquals(0, activeAlarm.getIsActive());
        assertNotNull(activeAlarm.getEndTs());
        assertEquals(2000L, activeAlarm.getEndTs());
        assertNotNull(activeAlarm.getDurationS());
        assertEquals(1000, activeAlarm.getDurationS()); // 2000 - 1000
    }

    @Test
    @DisplayName("TC-ALARM-004: 报警数组处理")
    void testMultipleAlarms() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        alarms.add(createAlarmMap("ALARM001", "报警1", "ERROR"));
        alarms.add(createAlarmMap("ALARM002", "报警2", "WARNING"));
        alarms.add(createAlarmMap("ALARM003", "报警3", "INFO"));
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then
        verify(deviceAlarmHistoryRepository, times(3)).insert(any(DeviceAlarmHistoryDO.class));
    }

    @Test
    @DisplayName("TC-ALARM-005: 报警级别更新")
    void testAlarmLevelUpdate() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        alarms.add(createAlarmMap("ALARM001", "温度过高", "ERROR")); // 级别从WARNING变为ERROR
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceAlarmHistoryDO existing = createAlarmHistory("ALARM001", "温度过高", "WARNING");
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.singletonList(existing));
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then
        verify(deviceAlarmHistoryRepository).updateById(existing);
        assertEquals("ERROR", existing.getAlarmLevel());
    }

    @Test
    @DisplayName("TC-ALARM-006: 报警文本更新")
    void testAlarmTextUpdate() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        alarms.add(createAlarmMap("ALARM001", "温度过高（更新）", "ERROR"));
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceAlarmHistoryDO existing = createAlarmHistory("ALARM001", "温度过高", "ERROR");
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.singletonList(existing));
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then
        verify(deviceAlarmHistoryRepository).updateById(existing);
        assertEquals("温度过高（更新）", existing.getAlarmText());
    }

    @Test
    @DisplayName("TC-ALARM-007: 空报警数组（关闭所有活跃报警）")
    void testEmptyAlarmArray() throws Exception {
        // Given - 正常的业务逻辑：空数组表示当前没有报警，应该关闭所有活跃报警
        List<Map<String, Object>> alarms = new ArrayList<>();
        
        WebhookRequest request = createAlarmRequest(alarms);
        request.setDataTimestamp(2000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceAlarmHistoryDO activeAlarm1 = createAlarmHistory("ALARM001", "报警1", "ERROR");
        activeAlarm1.setStartTs(1000L);
        DeviceAlarmHistoryDO activeAlarm2 = createAlarmHistory("ALARM002", "报警2", "WARNING");
        activeAlarm2.setStartTs(1000L);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Arrays.asList(activeAlarm1, activeAlarm2));
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then - 空数组时，所有活跃报警都应该被关闭
        verify(deviceAlarmHistoryRepository, times(2)).updateById(any(DeviceAlarmHistoryDO.class));
        assertEquals(0, activeAlarm1.getIsActive());
        assertEquals(0, activeAlarm2.getIsActive());
        assertNotNull(activeAlarm1.getEndTs());
        assertNotNull(activeAlarm2.getEndTs());
        assertEquals(2000L, activeAlarm1.getEndTs());
        assertEquals(2000L, activeAlarm2.getEndTs());
    }

    @Test
    @DisplayName("TC-ALARM-008: 报警数据格式兼容")
    void testAlarmDataFormatCompatibility() throws Exception {
        // Given - 单个对象而不是数组
        Map<String, Object> eventData = new HashMap<>();
        Map<String, Object> alarm = createAlarmMap("ALARM001", "温度过高", "ERROR");
        eventData.put("alarms", alarm); // 单个对象
        
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setEventType("DEVICE_ALARM");
        request.setDataTimestamp(2000L);
        request.setEventData(eventData);
        
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then - 应该正确处理为数组
        verify(deviceAlarmHistoryRepository).insert(any(DeviceAlarmHistoryDO.class));
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceAlarmEventHandler.supports("DEVICE_ALARM"));
        assertFalse(deviceAlarmEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(40, deviceAlarmEventHandler.order());
    }

    @Test
    @DisplayName("验证alarmCode为空时跳过")
    void testSkipAlarmWithEmptyCode() throws Exception {
        // Given
        List<Map<String, Object>> alarms = new ArrayList<>();
        alarms.add(createAlarmMap(null, "温度过高", "ERROR")); // alarmCode为空
        
        WebhookRequest request = createAlarmRequest(alarms);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(deviceAlarmHistoryRepository.findActiveByDevice(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // When
        deviceAlarmEventHandler.handle(inbox, request);
        
        // Then - 应该跳过alarmCode为空的报警
        verify(deviceAlarmHistoryRepository, never()).insert(any());
    }

    // 辅助方法
    private WebhookRequest createAlarmRequest(List<Map<String, Object>> alarms) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_ALARM");
        request.setDataTimestamp(2000L);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("alarms", alarms);
        request.setEventData(eventData);
        
        return request;
    }

    private Map<String, Object> createAlarmMap(String alarmCode, String alarmText, String alarmLevel) {
        Map<String, Object> alarm = new HashMap<>();
        alarm.put("alarmCode", alarmCode);
        alarm.put("alarmText", alarmText);
        alarm.put("alarmLevel", alarmLevel);
        return alarm;
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

    private DeviceAlarmHistoryDO createAlarmHistory(String alarmCode, String alarmText, String alarmLevel) {
        DeviceAlarmHistoryDO alarm = new DeviceAlarmHistoryDO();
        alarm.setAlarmCode(alarmCode);
        alarm.setAlarmText(alarmText);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setIsActive(1);
        alarm.setStartTs(1000L);
        alarm.setEndTs(null);
        alarm.setTenantUuid("tenant-001");
        alarm.setDeviceInfoId("device-info-001");
        return alarm;
    }
}

