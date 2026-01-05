package com.weili.iot_portal.service.assembler;

import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.domain.device.req.DeviceInfoSaveReqVO;
import com.weili.iot_portal.domain.device.req.DeviceLocationInfoReq;
import com.weili.iot_portal.domain.device.req.DeviceNetworkInfoReq;
import com.weili.iot_portal.domain.device.req.DeviceParamConfigReq;

import java.time.LocalDateTime;


public final class DeviceInfoAssembler {


    public static DeviceLocationDO createDeviceLocation(Long deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
        DeviceLocationInfoReq locationInfo = createReqVO.getLocation();
        DeviceLocationDO deviceLocation = new DeviceLocationDO();
        deviceLocation.setDeviceInfoId(deviceInfoId);
        deviceLocation.setOrgFactoryId(createReqVO.getOrgFactoryId());
        deviceLocation.setLocationCode(locationInfo.getLocationCode());
        deviceLocation.setLocationDescription(locationInfo.getLocationDescription());
        deviceLocation.setFloorNo(locationInfo.getFloorNo());
        deviceLocation.setAreaCode(locationInfo.getAreaCode());
        deviceLocation.setLongitude(locationInfo.getLongitude());
        deviceLocation.setLatitude(locationInfo.getLatitude());
        deviceLocation.setEffectiveStart(locationInfo.getEffectiveStart() != null
                ? locationInfo.getEffectiveStart()
                : LocalDateTime.now());
        deviceLocation.setEffectiveEnd(locationInfo.getEffectiveEnd());
        deviceLocation.setActive(true);
        deviceLocation.setDescription(locationInfo.getDescription());
        return deviceLocation;
    }


    public static DeviceLocationDO updateDeviceLocation(Long deviceInfoId,
                                                        DeviceLocationDO existing,
                                                        DeviceInfoSaveReqVO updateReqVO) {
        DeviceLocationInfoReq locationInfo = updateReqVO.getLocation();
        DeviceLocationDO deviceLocation;
        if (existing != null) {
            // 更新现有位置信息
            deviceLocation = existing;
        } else {
            // 创建新位置信息
            deviceLocation = new DeviceLocationDO();
            deviceLocation.setDeviceInfoId(deviceInfoId);
            deviceLocation.setEffectiveStart(LocalDateTime.now());
            deviceLocation.setActive(true);
        }

        deviceLocation.setOrgFactoryId(updateReqVO.getOrgFactoryId());
        deviceLocation.setLocationCode(locationInfo.getLocationCode());
        deviceLocation.setLocationDescription(locationInfo.getLocationDescription());
        deviceLocation.setFloorNo(locationInfo.getFloorNo());
        deviceLocation.setAreaCode(locationInfo.getAreaCode());
        deviceLocation.setLongitude(locationInfo.getLongitude());
        deviceLocation.setLatitude(locationInfo.getLatitude());
        if (locationInfo.getEffectiveStart() != null) {
            deviceLocation.setEffectiveStart(locationInfo.getEffectiveStart());
        }
        deviceLocation.setEffectiveEnd(locationInfo.getEffectiveEnd());
        deviceLocation.setDescription(locationInfo.getDescription());
        return deviceLocation;
    }


    public static DeviceNetworkConfigDO createNetworkConfigDO(Long deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
        DeviceNetworkInfoReq networkInfo = createReqVO.getNetwork();
        DeviceNetworkConfigDO deviceNetworkConfig = new DeviceNetworkConfigDO();
        deviceNetworkConfig.setDeviceInfoId(deviceInfoId);
        deviceNetworkConfig.setOrgFactoryId(createReqVO.getOrgFactoryId());
        deviceNetworkConfig.setIpAddress(networkInfo.getIpAddress());
        deviceNetworkConfig.setPort(networkInfo.getPort());
        deviceNetworkConfig.setMacAddress(networkInfo.getMacAddress());
        deviceNetworkConfig.setGateway(networkInfo.getGateway());
        deviceNetworkConfig.setSubnetMask(networkInfo.getSubnetMask());
        deviceNetworkConfig.setProtocol(networkInfo.getProtocol());
        deviceNetworkConfig.setEffectiveStart(networkInfo.getEffectiveStart() != null
                ? networkInfo.getEffectiveStart()
                : LocalDateTime.now());
        deviceNetworkConfig.setEffectiveEnd(networkInfo.getEffectiveEnd());
        deviceNetworkConfig.setIsActive(true);
        deviceNetworkConfig.setDescription(networkInfo.getDescription());
        return deviceNetworkConfig;
    }


    public static DeviceNetworkConfigDO updateNetworkConfigDO(Long deviceInfoId,
                                                              DeviceNetworkConfigDO existing,
                                                              DeviceInfoSaveReqVO updateReqVO) {
        DeviceNetworkInfoReq networkInfo = updateReqVO.getNetwork();
        DeviceNetworkConfigDO deviceNetworkConfig;
        if (existing != null) {
            // 更新现有网络配置
            deviceNetworkConfig = existing;
        } else {
            // 创建新网络配置
            deviceNetworkConfig = new DeviceNetworkConfigDO();
            deviceNetworkConfig.setDeviceInfoId(deviceInfoId);
            deviceNetworkConfig.setEffectiveStart(LocalDateTime.now());
            deviceNetworkConfig.setIsActive(true);
        }
        deviceNetworkConfig.setOrgFactoryId(updateReqVO.getOrgFactoryId());
        deviceNetworkConfig.setIpAddress(networkInfo.getIpAddress());
        deviceNetworkConfig.setPort(networkInfo.getPort());
        deviceNetworkConfig.setMacAddress(networkInfo.getMacAddress());
        deviceNetworkConfig.setGateway(networkInfo.getGateway());
        deviceNetworkConfig.setSubnetMask(networkInfo.getSubnetMask());
        deviceNetworkConfig.setProtocol(networkInfo.getProtocol());
        if (networkInfo.getEffectiveStart() != null) {
            deviceNetworkConfig.setEffectiveStart(networkInfo.getEffectiveStart());
        }
        deviceNetworkConfig.setEffectiveEnd(networkInfo.getEffectiveEnd());
        deviceNetworkConfig.setDescription(networkInfo.getDescription());
        return deviceNetworkConfig;
    }

    /**
     * 创建设备参数配置（用于新增和更新设备时的新版本记录）
     *
     * @param deviceInfoId 设备ID
     * @param paramConfigReq 参数配置请求
     * @param effectiveStartTs 生效开始时间戳
     * @return 设备参数配置DO
     */
    public static DeviceParamConfigDO createNewVersionParamConfig(Long deviceInfoId,
                                                                   DeviceParamConfigReq paramConfigReq,
                                                                   long effectiveStartTs) {
        DeviceParamConfigDO paramConfig = new DeviceParamConfigDO();
        paramConfig.setDeviceInfoId(String.valueOf(deviceInfoId));
        paramConfig.setParameterType(paramConfigReq.getParameterType());
        paramConfig.setParameterValue(paramConfigReq.getParameterValue());
        paramConfig.setEffectiveStartTs(effectiveStartTs);
        paramConfig.setEffectiveEndTs(null); // NULL表示当前生效
        paramConfig.setIsActive(true);
        return paramConfig;
    }
}
