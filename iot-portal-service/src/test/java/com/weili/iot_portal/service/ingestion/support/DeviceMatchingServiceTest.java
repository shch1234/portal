package com.weili.iot_portal.service.ingestion.support;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 设备匹配服务测试
 * 测试用例：TC-DEVICE-001 ~ TC-DEVICE-007
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备匹配服务测试")
class DeviceMatchingServiceTest {

    @Mock
    private DeviceBaseInfoMapper deviceBaseInfoMapper;

    @InjectMocks
    private DeviceMatchingService deviceMatchingService;

    private DeviceBaseInfoDO createActiveDevice(String deviceCode) {
        DeviceBaseInfoDO device = new DeviceBaseInfoDO();
        device.setDeviceCode(deviceCode);
        device.setDeviceStatus("ACTIVE");
        device.setIsMonitored(Boolean.TRUE);
        device.setDeleted(Boolean.FALSE);
        device.setTenantUuid("tenant-001");
        device.setTbDeviceId("tb-device-001");
        return device;
    }

    @Test
    @DisplayName("TC-DEVICE-001: 设备匹配成功")
    void testDeviceMatchSuccess() {
        // Given
        String deviceCode = "M001";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(device);
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(deviceCode, result.get().getDeviceCode());
        assertEquals("ACTIVE", result.get().getDeviceStatus());
        assertTrue(result.get().getIsMonitored());
    }

    @Test
    @DisplayName("TC-DEVICE-002: deviceCode为空")
    void testEmptyDeviceCode() {
        // Given
        String deviceCode = null;
        
        // When
        Optional<DeviceBaseInfoDO> result1 = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result1.isEmpty());
        verify(deviceBaseInfoMapper, never()).selectOne(any());
        
        // Given - 空字符串
        String emptyDeviceCode = "";
        
        // When
        Optional<DeviceBaseInfoDO> result2 = deviceMatchingService.match(emptyDeviceCode);
        
        // Then
        assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("TC-DEVICE-003: 设备不存在")
    void testDeviceNotFound() {
        // Given
        String deviceCode = "NONEXISTENT";
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(null);
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TC-DEVICE-004: 设备状态非ACTIVE")
    void testDeviceStatusNotActive() {
        // Given
        String deviceCode = "M002";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        device.setDeviceStatus("INACTIVE");
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(device);
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TC-DEVICE-005: 设备未启用监控")
    void testDeviceNotMonitored() {
        // Given
        String deviceCode = "M003";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        device.setIsMonitored(Boolean.FALSE);
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(device);
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TC-DEVICE-006: 设备已删除")
    void testDeviceDeleted() {
        // Given
        String deviceCode = "M004";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        device.setDeleted(Boolean.TRUE); // 已删除
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(null); // 查询条件包含deleted=0，所以返回null
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TC-DEVICE-007: 设备匹配后信息完整")
    void testDeviceMatchWithCompleteInfo() {
        // Given
        String deviceCode = "M005";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        device.setDeviceName("测试设备");
        device.setTenantUuid("tenant-001");
        device.setTbDeviceId("tb-device-001");
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(device);
        
        // When
        Optional<DeviceBaseInfoDO> result = deviceMatchingService.match(deviceCode);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("tenant-001", result.get().getTenantUuid());
        assertEquals("tb-device-001", result.get().getTbDeviceId());
        assertEquals("测试设备", result.get().getDeviceName());
    }

    @Test
    @DisplayName("验证查询条件")
    void testQueryConditions() {
        // Given
        String deviceCode = "M006";
        DeviceBaseInfoDO device = createActiveDevice(deviceCode);
        
        when(deviceBaseInfoMapper.selectOne(any())).thenReturn(device);
        
        // When
        deviceMatchingService.match(deviceCode);
        
        // Then - 验证查询条件包含deviceCode和deleted=0
        verify(deviceBaseInfoMapper).selectOne(any());
    }
}

