package com.weili.iot_portal.business.device_mgmt.api;

import java.util.List;
import java.util.Map;

/**
 * 设备状态服务接口（供其他模块调用）
 * 
 * <p>提供设备状态相关的查询服务，其他模块可以通过此接口获取设备状态信息。
 * 
 * <p><b>注意：所有方法都需要提供factoryId参数，以确保工厂数据隔离。</b>
 * 如果设备不属于指定的工厂，将抛出ServiceException。
 */
public interface DeviceStateService {

    /**
     * 设备状态VO（简化版，仅包含状态信息）
     */
    class DeviceStatusVO {
        private String deviceId;
        private String deviceCode;
        private String deviceName;
        private String currentStatus; // 加工中/待机/故障/关机
        private Long statusChangeTs; // 状态变更时间戳
        private Boolean hasAlarm; // 是否有报警

        // Getters and Setters
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceCode() { return deviceCode; }
        public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getCurrentStatus() { return currentStatus; }
        public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
        public Long getStatusChangeTs() { return statusChangeTs; }
        public void setStatusChangeTs(Long statusChangeTs) { this.statusChangeTs = statusChangeTs; }
        public Boolean getHasAlarm() { return hasAlarm; }
        public void setHasAlarm(Boolean hasAlarm) { this.hasAlarm = hasAlarm; }
    }

    /**
     * 获取设备当前状态
     * 
     * <p><b>工厂隔离：</b>会验证设备是否属于指定的工厂，如果设备不属于该工厂，将抛出异常。
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 设备状态信息，如果设备不存在或不属于指定工厂则返回null或抛出异常
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不属于指定工厂
     */
    DeviceStatusVO getCurrentStatus(String tenantId, String factoryId, String deviceId);

    /**
     * 批量获取设备状态
     * 
     * <p><b>工厂隔离：</b>会验证所有设备是否属于指定的工厂，只返回属于该工厂的设备状态。
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceIds 设备ID列表
     * @return 属于指定工厂的设备状态Map，key为deviceId，value为DeviceStatusVO
     */
    Map<String, DeviceStatusVO> batchGetStatus(String tenantId, String factoryId, List<String> deviceIds);

    /**
     * 检查设备是否有报警
     * 
     * <p><b>工厂隔离：</b>会验证设备是否属于指定的工厂。
     *
     * @param tenantId 租户ID
     * @param factoryId 工厂ID（用于数据隔离验证）
     * @param deviceId 设备ID
     * @return 是否有报警（如果设备不属于指定工厂，返回false或抛出异常）
     * @throws com.weili.basic.common.exception.ServiceException 如果设备不属于指定工厂
     */
    boolean hasAlarm(String tenantId, String factoryId, String deviceId);
}

