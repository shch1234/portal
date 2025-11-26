package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceConfigurationDO;
import com.weili.iot_portal.domain.devicebase.DeviceConfigurationVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceConfigurationCreateReq;

import java.util.UUID;

/**
 * 设备配置装配器
 */
public final class DeviceConfigurationAssembler {

    private DeviceConfigurationAssembler() {
    }

    public static DeviceConfigurationDO fromCreateReq(String tenantId, DeviceConfigurationCreateReq request) {
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
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime())
                .setUpdatedBy(entity.getUpdatedBy());
    }
}

