package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateTimelineMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.ingestion.WebhookFailLogService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备状态事件处理器测试
 * 测试用例：TC-STATE-001 ~ TC-STATE-010
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备状态事件处理器测试")
class DeviceStateEventHandlerTest {

    @Mock
    private DeviceStateTimelineRepository stateTimelineRepository;

    @Mock
    private DeviceStateTimelineMapper stateTimelineMapper;

    @Mock
    private DeviceIdentityCacheService deviceIdentityCacheService;

    @Mock
    private WebhookFailLogService webhookFailLogService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RealTimeCacheService realTimeCacheService;

    @InjectMocks
    private DeviceStateEventHandler deviceStateEventHandler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deviceStateEventHandler, "stateTtlMillis", 600000L);
        ReflectionTestUtils.setField(deviceStateEventHandler, "stateHeartbeatTtlSeconds", 300L);
        
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("TC-STATE-001: 首次连接（previousState=null）")
    void testFirstConnection() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", null);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.empty());
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceStateTimelineDO> captor = ArgumentCaptor.forClass(DeviceStateTimelineDO.class);
        verify(stateTimelineMapper).insert(captor.capture());
        
        DeviceStateTimelineDO saved = captor.getValue();
        assertEquals("WORKING", saved.getStateCode());
        assertNull(saved.getEndTs());
        assertTrue(saved.getIsComplete());
    }

    @Test
    @DisplayName("TC-STATE-002: 正常状态转换")
    void testNormalStateTransition() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("STANDBY", "WORKING");
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceStateTimelineDO latestState = createStateTimeline("WORKING", 1000L, null);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.of(latestState));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then
        verify(stateTimelineMapper).updateById(latestState);
        assertEquals(2000L, latestState.getEndTs()); // 事件时间戳
        assertTrue(latestState.getIsComplete());
        
        ArgumentCaptor<DeviceStateTimelineDO> captor = ArgumentCaptor.forClass(DeviceStateTimelineDO.class);
        verify(stateTimelineMapper).insert(captor.capture());
        assertEquals("STANDBY", captor.getValue().getStateCode());
    }

    @Test
    @DisplayName("TC-STATE-003: 状态未变化")
    void testStateUnchanged() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", "WORKING");
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceStateTimelineDO latestState = createStateTimeline("WORKING", 1000L, null);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.of(latestState));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then - 不插入新记录，只刷新缓存TTL和心跳
        verify(stateTimelineMapper, never()).insert(any(DeviceStateTimelineDO.class));
        verify(stateTimelineMapper, never()).updateById(any(DeviceStateTimelineDO.class));
        // 状态未变化时会调用refreshStateCacheAndHeartbeat，它会调用redisTemplate.expire和refreshHeartbeat
        verify(redisTemplate).expire(anyString(), any(java.time.Duration.class));
        verify(realTimeCacheService).setWithTtlSeconds(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("TC-STATE-004: 状态不匹配（DB状态已结束）")
    void testStateMismatchEnded() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("FAULT", "STANDBY");
        request.setDataTimestamp(3000L);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceStateTimelineDO latestState = createStateTimeline("WORKING", 1000L, 2000L);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.of(latestState));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then - 应该插入UNKNOWN状态填充间隙，然后插入新状态
        verify(stateTimelineMapper, times(2)).insert(any(DeviceStateTimelineDO.class));
        verify(webhookFailLogService).saveFailLog(any(), eq("STATE_MISMATCH"), anyString(), eq(false));
    }

    @Test
    @DisplayName("TC-STATE-005: 状态不匹配（DB状态进行中）")
    void testStateMismatchOngoing() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("FAULT", "STANDBY");
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceStateTimelineDO latestState = createStateTimeline("WORKING", 1000L, null);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.of(latestState));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then - 旧状态标记为UNKNOWN，插入新状态
        verify(stateTimelineMapper).updateById(latestState);
        assertEquals("UNKNOWN", latestState.getStateCode());
        verify(stateTimelineMapper, atLeastOnce()).insert(any(DeviceStateTimelineDO.class));
        verify(webhookFailLogService).saveFailLog(any(), eq("STATE_MISMATCH"), anyString(), eq(true));
    }

    @Test
    @DisplayName("TC-STATE-006: 时间戳异常")
    void testTimestampAnomaly() throws Exception {
        // Given
        // 时间戳异常的场景：事件时间早于DB状态开始时间
        // 需要满足：1. previousState与latestState不匹配（进入handleStateMismatch）
        //          2. latestState是ongoing状态（endTs为null）
        //          3. eventTimestamp < latestState.getStartTs()
        WebhookRequest request = createStateRequest("FAULT", "STANDBY"); // previousState与DB状态WORKING不匹配
        request.setDataTimestamp(500L); // 早于DB状态开始时间1000L（秒级时间戳）
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        DeviceStateTimelineDO latestState = createStateTimeline("WORKING", 1000L, null); // ongoing状态
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.of(latestState));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        long beforeTime = System.currentTimeMillis() / 1000;
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        long afterTime = System.currentTimeMillis() / 1000;
        
        // Then - 使用当前时间作为start_ts（秒级时间戳）
        ArgumentCaptor<DeviceStateTimelineDO> captor = ArgumentCaptor.forClass(DeviceStateTimelineDO.class);
        verify(stateTimelineMapper).insert(captor.capture());
        long insertedStartTs = captor.getValue().getStartTs();
        // 允许1秒的误差，因为beforeTime和afterTime可能跨秒
        assertTrue(insertedStartTs >= beforeTime - 1 && insertedStartTs <= afterTime + 1, 
                String.format("应该使用当前时间作为start_ts: insertedStartTs=%d, beforeTime=%d, afterTime=%d", 
                        insertedStartTs, beforeTime, afterTime));
        verify(webhookFailLogService).saveFailLog(any(), eq("TIMESTAMP_ANOMALY"), anyString(), eq(true));
    }

    @Test
    @DisplayName("TC-STATE-007: 状态心跳事件")
    void testStateHeartbeat() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", null);
        request.setEventType("DEVICE_STATE_HEARTBEAT");
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then - 不写入时间线，只刷新缓存
        verify(stateTimelineMapper, never()).insert(any(DeviceStateTimelineDO.class));
        verify(stateTimelineMapper, never()).updateById(any(DeviceStateTimelineDO.class));
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("TC-STATE-008: 实时状态缓存")
    void testRealtimeStateCache() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", null);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.empty());
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then
        verify(realTimeCacheService).hsetWithTtl(anyString(), any(), eq(600000L));
    }

    @Test
    @DisplayName("TC-STATE-009: 分布式锁机制")
    void testDistributedLock() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", null);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.empty());
        
        // 模拟获取锁失败
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(false);
        
        // When & Then
        assertThrows(Exception.class, () -> {
            deviceStateEventHandler.handle(inbox, request);
        });
        
        verify(stateTimelineMapper, never()).insert(any(DeviceStateTimelineDO.class));
    }

    @Test
    @DisplayName("TC-STATE-010: 状态记录完整性")
    void testStateRecordIntegrity() throws Exception {
        // Given
        WebhookRequest request = createStateRequest("WORKING", null);
        WebhookInboxDO inbox = createInbox();
        
        DeviceIdentityCacheService.DeviceIdentity identity = createIdentity();
        when(deviceIdentityCacheService.resolveByDeviceCode(any(), any(), any(), any()))
                .thenReturn(identity);
        when(stateTimelineRepository.findLatestState(any(), any()))
                .thenReturn(Optional.empty());
        when(valueOperations.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true);
        
        // When
        deviceStateEventHandler.handle(inbox, request);
        
        // Then
        ArgumentCaptor<DeviceStateTimelineDO> captor = ArgumentCaptor.forClass(DeviceStateTimelineDO.class);
        verify(stateTimelineMapper).insert(captor.capture());
        
        DeviceStateTimelineDO saved = captor.getValue();
        assertNotNull(saved.getTenantUuid());
        assertNotNull(saved.getDeviceInfoId());
        assertNotNull(saved.getStateCode());
        assertNotNull(saved.getStartTs());
    }

    @Test
    @DisplayName("验证supports方法")
    void testSupports() {
        assertTrue(deviceStateEventHandler.supports("DEVICE_STATE"));
        assertTrue(deviceStateEventHandler.supports("DEVICE_STATE_HEARTBEAT"));
        assertFalse(deviceStateEventHandler.supports("OTHER_EVENT"));
    }

    @Test
    @DisplayName("验证order方法")
    void testOrder() {
        assertEquals(10, deviceStateEventHandler.order());
    }

    // 辅助方法
    private WebhookRequest createStateRequest(String currentState, String previousState) {
        WebhookRequest request = new WebhookRequest();
        request.setMessageId("msg-001");
        request.setTenantId("tenant-001");
        request.setDeviceCode("M001");
        request.setDeviceId("tb-device-001");
        request.setEventType("DEVICE_STATE");
        request.setDataTimestamp(2000L);
        
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("currentState", currentState);
        if (previousState != null) {
            eventData.put("previousState", previousState);
        }
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

    private DeviceStateTimelineDO createStateTimeline(String stateCode, Long startTs, Long endTs) {
        DeviceStateTimelineDO timeline = new DeviceStateTimelineDO();
        timeline.setStateCode(stateCode);
        timeline.setStartTs(startTs);
        timeline.setEndTs(endTs);
        timeline.setIsComplete(endTs != null);
        timeline.setTenantUuid("tenant-001");
        timeline.setDeviceInfoId("device-info-001");
        return timeline;
    }
}

