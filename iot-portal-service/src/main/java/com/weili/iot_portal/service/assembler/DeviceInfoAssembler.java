package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.domain.device.req.DeviceInfoSaveReqVO;


public final class DeviceInfoAssembler {


    public static DeviceLocationDO createDeviceLocation(String deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
        DeviceInfoSaveReqVO.DeviceLocationInfo locationInfo = createReqVO.getLocation();
        DeviceLocationDO deviceLocation = new DeviceLocationDO();
        deviceLocation.setDeviceInfoId(deviceInfoId);
        deviceLocation.setOrgFactoryId(createReqVO.getOrgFactoryId());
        deviceLocation.setLocationCode(locationInfo.getLocationCode());
        deviceLocation.setLocationDescription(locationInfo.getLocationDescription());
        deviceLocation.setCoordinates(locationInfo.getCoordinates());
        deviceLocation.setFloorNo(locationInfo.getFloorNo());
        deviceLocation.setAreaCode(locationInfo.getAreaCode());
        deviceLocation.setLongitude(locationInfo.getLongitude());
        deviceLocation.setLatitude(locationInfo.getLatitude());
        deviceLocation.setEffectiveStartTs(locationInfo.getEffectiveStartTs() != null
                ? locationInfo.getEffectiveStartTs()
                : System.currentTimeMillis() / 1000);
        deviceLocation.setEffectiveEndTs(locationInfo.getEffectiveEndTs());
        deviceLocation.setActive(true);
        deviceLocation.setDescription(locationInfo.getDescription());
        return deviceLocation;
    }


    public static DeviceLocationDO updateDeviceLocation(String deviceInfoId,
                                                        DeviceLocationDO existing,
                                                        DeviceInfoSaveReqVO updateReqVO) {
        DeviceInfoSaveReqVO.DeviceLocationInfo locationInfo = updateReqVO.getLocation();
        DeviceLocationDO deviceLocation;
        if (existing != null) {
            // 更新现有位置信息
            deviceLocation = existing;
        } else {
            // 创建新位置信息
            deviceLocation = new DeviceLocationDO();
            deviceLocation.setDeviceInfoId(deviceInfoId);
            deviceLocation.setEffectiveStartTs(System.currentTimeMillis() / 1000);
            deviceLocation.setActive(true);
        }

        deviceLocation.setOrgFactoryId(updateReqVO.getOrgFactoryId());
        deviceLocation.setLocationCode(locationInfo.getLocationCode());
        deviceLocation.setLocationDescription(locationInfo.getLocationDescription());
        deviceLocation.setCoordinates(locationInfo.getCoordinates());
        deviceLocation.setFloorNo(locationInfo.getFloorNo());
        deviceLocation.setAreaCode(locationInfo.getAreaCode());
        deviceLocation.setLongitude(locationInfo.getLongitude());
        deviceLocation.setLatitude(locationInfo.getLatitude());
        if (locationInfo.getEffectiveStartTs() != null) {
            deviceLocation.setEffectiveStartTs(locationInfo.getEffectiveStartTs());
        }
        deviceLocation.setEffectiveEndTs(locationInfo.getEffectiveEndTs());
        deviceLocation.setDescription(locationInfo.getDescription());
        return deviceLocation;
    }


    public static DeviceNetworkConfigDO createNetworkConfigDO(String deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
        DeviceInfoSaveReqVO.DeviceNetworkInfo networkInfo = createReqVO.getNetwork();
        DeviceNetworkConfigDO deviceNetworkConfig = new DeviceNetworkConfigDO();
        deviceNetworkConfig.setDeviceInfoId(deviceInfoId);
        deviceNetworkConfig.setOrgFactoryId(createReqVO.getOrgFactoryId());
        deviceNetworkConfig.setIpAddress(networkInfo.getIpAddress());
        deviceNetworkConfig.setPort(networkInfo.getPort());
        deviceNetworkConfig.setMacAddress(networkInfo.getMacAddress());
        deviceNetworkConfig.setGateway(networkInfo.getGateway());
        deviceNetworkConfig.setSubnetMask(networkInfo.getSubnetMask());
        deviceNetworkConfig.setProtocol(networkInfo.getProtocol());
        deviceNetworkConfig.setConnectionParams(networkInfo.getConnectionParams());
        deviceNetworkConfig.setEffectiveStartTs(networkInfo.getEffectiveStartTs() != null
                ? networkInfo.getEffectiveStartTs()
                : System.currentTimeMillis() / 1000);
        deviceNetworkConfig.setEffectiveEndTs(networkInfo.getEffectiveEndTs());
        deviceNetworkConfig.setIsActive(true);
        deviceNetworkConfig.setDescription(networkInfo.getDescription());
        return deviceNetworkConfig;
    }


    public static DeviceNetworkConfigDO updateNetworkConfigDO(String deviceInfoId,
                                                              DeviceNetworkConfigDO existing,
                                                              DeviceInfoSaveReqVO updateReqVO) {
        DeviceInfoSaveReqVO.DeviceNetworkInfo networkInfo = updateReqVO.getNetwork();
        DeviceNetworkConfigDO deviceNetworkConfig;
        if (existing != null) {
            // 更新现有网络配置
            deviceNetworkConfig = existing;
        } else {
            // 创建新网络配置
            deviceNetworkConfig = new DeviceNetworkConfigDO();
            deviceNetworkConfig.setDeviceInfoId(deviceInfoId);
            deviceNetworkConfig.setEffectiveStartTs(System.currentTimeMillis() / 1000);
            deviceNetworkConfig.setIsActive(true);
        }
        deviceNetworkConfig.setOrgFactoryId(updateReqVO.getOrgFactoryId());
        deviceNetworkConfig.setIpAddress(networkInfo.getIpAddress());
        deviceNetworkConfig.setPort(networkInfo.getPort());
        deviceNetworkConfig.setMacAddress(networkInfo.getMacAddress());
        deviceNetworkConfig.setGateway(networkInfo.getGateway());
        deviceNetworkConfig.setSubnetMask(networkInfo.getSubnetMask());
        deviceNetworkConfig.setProtocol(networkInfo.getProtocol());
        deviceNetworkConfig.setConnectionParams(networkInfo.getConnectionParams());
        if (networkInfo.getEffectiveStartTs() != null) {
            deviceNetworkConfig.setEffectiveStartTs(networkInfo.getEffectiveStartTs());
        }
        deviceNetworkConfig.setEffectiveEndTs(networkInfo.getEffectiveEndTs());
        deviceNetworkConfig.setDescription(networkInfo.getDescription());
        return deviceNetworkConfig;
    }


}
