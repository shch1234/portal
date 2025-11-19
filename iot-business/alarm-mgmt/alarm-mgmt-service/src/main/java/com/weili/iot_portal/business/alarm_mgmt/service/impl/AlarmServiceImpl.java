package com.weili.iot_portal.business.alarm_mgmt.service.impl;

import com.weili.iot_portal.business.common.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService.DeviceStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 报警服务实现示例
 * 
 * <p>本类展示了报警管理模块如何通过device-mgmt-api接口获取设备信息，
 * 而不是直接依赖设备管理模块的实现层。这是模块间解耦的最佳实践。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl {

    // 通过API接口依赖设备管理模块，而非实现
    private final DeviceBaseDataApi deviceBaseDataApi;
    private final DeviceStateService deviceStateService;

    /**
     * 处理报警事件的示例方法
     * 
     * <p>通过device-mgmt-api获取设备信息，实现模块间解耦。
     * <p><b>注意：</b>所有API调用都需要提供factoryId参数，以确保工厂数据隔离。
     */
    public void processAlarm(String tenantId, String factoryId, String deviceId, String alarmCode, String alarmMessage) {
        // 通过API接口获取设备信息（API内部会进行工厂验证）
        var device = deviceBaseDataApi.getDeviceById(tenantId, factoryId, deviceId);
        if (device == null) {
            log.warn("设备不存在或不属于指定工厂，忽略报警: tenantId={}, factoryId={}, deviceId={}", 
                    tenantId, factoryId, deviceId);
            return;
        }

        // 通过API接口获取设备状态（API内部会进行工厂验证）
        DeviceStatusVO status = deviceStateService.getCurrentStatus(tenantId, factoryId, deviceId);
        
        log.info("处理报警: deviceCode={}, deviceName={}, status={}, alarmCode={}, alarmMessage={}",
                device.getDeviceCode(), device.getDeviceName(), 
                status != null ? status.getCurrentStatus() : "unknown",
                alarmCode, alarmMessage);
        
        // 后续的报警处理逻辑...
    }
}

