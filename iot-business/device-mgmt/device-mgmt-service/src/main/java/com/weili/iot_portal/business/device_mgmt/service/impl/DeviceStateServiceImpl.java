package com.weili.iot_portal.business.device_mgmt.service.impl;

import com.weili.iot_portal.business.common.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService;
import com.weili.iot_portal.business.device_mgmt.api.DeviceStateService.DeviceStatusVO;
import com.weili.iot_portal.business.device_mgmt.service.AlarmHistoryService;
import com.weili.iot_portal.business.device_mgmt.service.support.DeviceFactoryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态服务实现
 * 
 * <p>实现device-mgmt-api中定义的设备状态服务接口。
 * 所有方法都会进行工厂数据隔离验证，确保调用者只能访问属于其工厂的设备状态。
 */
@Service
@RequiredArgsConstructor
public class DeviceStateServiceImpl implements DeviceStateService {

    private final DeviceBaseDataApi deviceBaseDataApi;
    private final AlarmHistoryService alarmHistoryService;
    private final DeviceFactoryValidator deviceFactoryValidator;

    @Override
    public DeviceStatusVO getCurrentStatus(String tenantId, String factoryId, String deviceId) {
        // 先验证设备是否属于指定工厂
        deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
        
        // 通过公共API查询设备信息（内部会进行工厂验证）
        var device = deviceBaseDataApi.getDeviceById(tenantId, factoryId, deviceId);
        if (device == null) {
            return null;
        }

        DeviceStatusVO status = new DeviceStatusVO();
        status.setDeviceId(device.getId());
        status.setDeviceCode(device.getDeviceCode());
        status.setDeviceName(device.getDeviceName());
        
        // TODO: 从实时数据或状态表获取当前状态
        // status.setCurrentStatus(...);
        // status.setStatusChangeTs(...);
        
        // 检查是否有报警（alarmHistoryService内部会进行工厂验证）
        boolean hasAlarm = alarmHistoryService.hasActiveAlarm(tenantId, factoryId, deviceId);
        status.setHasAlarm(hasAlarm);
        
        return status;
    }

    @Override
    public Map<String, DeviceStatusVO> batchGetStatus(String tenantId, String factoryId, List<String> deviceIds) {
        Map<String, DeviceStatusVO> result = new HashMap<>();
        for (String deviceId : deviceIds) {
            try {
                DeviceStatusVO status = getCurrentStatus(tenantId, factoryId, deviceId);
                if (status != null) {
                    result.put(deviceId, status);
                }
            } catch (Exception e) {
                // 如果设备不存在或不属于指定工厂，则跳过
            }
        }
        return result;
    }

    @Override
    public boolean hasAlarm(String tenantId, String factoryId, String deviceId) {
        try {
            // 先验证设备是否属于指定工厂
            deviceFactoryValidator.ensureDeviceBelongsToFactory(tenantId, factoryId, deviceId);
            return alarmHistoryService.hasActiveAlarm(tenantId, factoryId, deviceId);
        } catch (Exception e) {
            return false;
        }
    }
}

