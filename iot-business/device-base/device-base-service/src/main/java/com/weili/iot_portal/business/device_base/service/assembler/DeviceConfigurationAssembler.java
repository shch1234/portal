package com.weili.iot_portal.business.device_base.service.assembler;

import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceConfigurationDO;
import com.weili.iot_portal.business.device_base.domain.model.DeviceConfigurationVO;
import com.weili.iot_portal.business.device_base.domain.model.request.DeviceConfigurationCreateReq;

import java.time.ZoneId;
import java.util.UUID;

/**
 * 设备配置装配器
 */
public final class DeviceConfigurationAssembler {

    private DeviceConfigurationAssembler() {
    }

    public static DeviceConfigurationDO fromCreateReq(String tenantId, String operator, DeviceConfigurationCreateReq request) {
        DeviceConfigurationDO entity = new DeviceConfigurationDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(tenantId);
        entity.setDeviceId(request.getDeviceId());
        entity.setIpAddress(request.getIpAddress());
        entity.setPort(request.getPort());
        entity.setMacAddress(request.getMacAddress());
        entity.setGateway(request.getGateway());
        entity.setSubnetMask(request.getSubnetMask());
        entity.setProtocol(request.getProtocol());
        entity.setConnectionParams(request.getConnectionParams());
        entity.setLocationCode(request.getLocationCode());
        entity.setLocationDescription(request.getLocationDescription());
        entity.setCoordinates(request.getCoordinates());
        entity.setUpdatedBy(operator);
        return entity;
    }

    public static DeviceConfigurationVO toVO(DeviceConfigurationDO entity, String deviceCode, String deviceName) {
        return new DeviceConfigurationVO()
                .setId(entity.getId())
                .setDeviceId(entity.getDeviceId())
                .setDeviceCode(deviceCode)
                .setDeviceName(deviceName)
                .setIpAddress(entity.getIpAddress())
                .setPort(entity.getPort())
                .setMacAddress(entity.getMacAddress())
                .setGateway(entity.getGateway())
                .setSubnetMask(entity.getSubnetMask())
                .setProtocol(entity.getProtocol())
                .setConnectionParams(entity.getConnectionParams())
                .setLocationCode(entity.getLocationCode())
                .setLocationDescription(entity.getLocationDescription())
                .setCoordinates(entity.getCoordinates())
                .setCreatedTime(toEpochMilli(entity.getCreateTime()))
                .setUpdatedTime(toEpochMilli(entity.getUpdateTime()))
                .setUpdatedBy(entity.getUpdatedBy());
    }

    private static Long toEpochMilli(java.time.LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}

