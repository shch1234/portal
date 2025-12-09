package com.weili.iot_portal.task.devicemng;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
import com.weili.iot_portal.dal.repository.devicemng.DeviceParameterRepository;
import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateSummaryRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceMetricsShiftMapper;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 设备班次指标汇总任务测试
 * 覆盖TC-JOB-METRICS-002
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("设备班次指标汇总任务测试")
class DeviceMetricsSummaryJobTest {

    @Mock
    private DeviceBaseInfoMapper deviceBaseInfoMapper;

    @Mock
    private DeviceStateSummaryRepository deviceStateSummaryRepository;

    @Mock
    private DeviceParameterRepository deviceParameterRepository;

    @Mock
    private DeviceMetricsShiftMapper deviceMetricsShiftMapper;

    @Mock
    private DeviceProductionRecordRepository deviceProductionRecordRepository;

    @Mock
    private ShiftConfigurationService shiftConfigurationService;

    @InjectMocks
    private DeviceMetricsSummaryJob deviceMetricsSummaryJob;

    private DeviceBaseInfoDO device;

    @BeforeEach
    void setUp() {
        device = new DeviceBaseInfoDO();
        device.setDeviceCode("M001");
        device.setTenantUuid("tenant-001");
        device.setOrgFactoryId("factory-001");
        device.setTbDeviceId("tb-device-001");
    }

    @Test
    @DisplayName("TC-JOB-METRICS-002: 班次指标汇总")
    void testShiftMetricsSummary() throws Exception {
        // Given - 准备班次状态汇总数据
        List<DeviceBaseInfoDO> devices = Collections.singletonList(device);
        when(deviceBaseInfoMapper.selectList(any())).thenReturn(devices);

        // When - 执行指标汇总任务
        assertDoesNotThrow(() -> deviceMetricsSummaryJob.execute());
    }

    @Test
    @DisplayName("班次指标汇总任务 - 无设备场景")
    void testNoDevicesScenario() throws Exception {
        // Given - 无设备
        when(deviceBaseInfoMapper.selectList(any())).thenReturn(Collections.emptyList());

        // When - 执行指标汇总任务
        assertDoesNotThrow(() -> deviceMetricsSummaryJob.execute());
        
        // Then - 应该正常返回，不处理任何设备
    }
}

