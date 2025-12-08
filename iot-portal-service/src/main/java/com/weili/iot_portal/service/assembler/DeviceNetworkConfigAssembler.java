package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.devicebase.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceNetworkConfigDO;
import com.weili.iot_portal.domain.devicebase.DeviceNetworkConfigVO;
import com.weili.iot_portal.domain.devicebase.request.DeviceNetworkConfigCreateReq;

import java.util.UUID;

public final class DeviceNetworkConfigAssembler {

    private DeviceNetworkConfigAssembler() {
    }

    public static DeviceNetworkConfigDO fromCreateReq(String tenantId, DeviceNetworkConfigCreateReq request) {
        DeviceNetworkConfigDO entity = new DeviceNetworkConfigDO();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantUuid(tenantId);
        entity.setDeviceInfoId(request.getDeviceId());
        entity.setIpAddress(request.getIpAddress());
        entity.setPort(request.getPort());
        entity.setMacAddress(request.getMacAddress());
        entity.setGateway(request.getGateway());
        entity.setSubnetMask(request.getSubnetMask());
        entity.setProtocol(request.getProtocol());
        entity.setConnectionParams(request.getConnectionParams());
        return entity;
    }

    public static DeviceLocationDO toLocationFromCreateReq(String tenantId, DeviceNetworkConfigCreateReq request) {
        DeviceLocationDO location = new DeviceLocationDO();
        location.setId(UUID.randomUUID().toString());
        location.setTenantUuid(tenantId);
        location.setDeviceInfoId(request.getDeviceId());
        location.setLocationCode(request.getLocationCode());
        location.setLocationDescription(request.getLocationDescription());
        location.setCoordinates(request.getCoordinates());
        return location;
    }

    public static DeviceNetworkConfigVO toVO(DeviceNetworkConfigDO entity, DeviceLocationDO location, String deviceCode, String deviceName) {
        return new DeviceNetworkConfigVO()
                .setId(entity.getId())
                .setDeviceId(entity.getDeviceInfoId())
                .setDeviceCode(deviceCode)
                .setDeviceName(deviceName)
                .setIpAddress(entity.getIpAddress())
                .setPort(entity.getPort())
                .setMacAddress(entity.getMacAddress())
                .setGateway(entity.getGateway())
                .setSubnetMask(entity.getSubnetMask())
                .setProtocol(entity.getProtocol())
                .setConnectionParams(entity.getConnectionParams())
                .setLocationCode(location != null ? location.getLocationCode() : null)
                .setLocationDescription(location != null ? location.getLocationDescription() : null)
                .setCoordinates(location != null ? location.getCoordinates() : null)
                .setCreateTime(entity.getCreateTime())
                .setUpdateTime(entity.getUpdateTime())
                .setUpdatedBy(entity.getUpdatedBy());
    }
}

