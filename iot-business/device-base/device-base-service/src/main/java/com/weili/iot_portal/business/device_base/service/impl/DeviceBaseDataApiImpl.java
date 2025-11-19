package com.weili.iot_portal.business.device_base.service.impl;

import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.business.common.api.device.DeviceBaseDataApi;
import com.weili.iot_portal.business.common.domain.model.device.DeviceBaseInfoVO;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 设备基础数据API实现
 * 
 * <p>实现公共接口 DeviceBaseDataApi，提供设备基础数据查询功能
 * 供其他业务模块（alarm-mgmt、efficiency-mgmt等）调用
 */
@Service
@RequiredArgsConstructor
public class DeviceBaseDataApiImpl implements DeviceBaseDataApi {

    private final DeviceBaseInfoRepository deviceBaseInfoRepository;
    private final DeviceConfigurationRepository deviceConfigurationRepository;

    @Override
    public DeviceBaseInfoVO getDeviceById(String tenantId, String factoryId, String deviceId) {
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new ServiceException(
                        com.weili.basic.common.enums.ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "设备不存在"));
        
        // 验证设备是否属于指定工厂
        if (factoryId != null && !factoryId.equals(device.getFactoryId())) {
            throw new ServiceException(
                    com.weili.basic.common.enums.ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                    "设备不属于指定工厂");
        }
        
        return convertToCommonVO(device);
    }

    @Override
    public DeviceBaseInfoVO getDeviceByCode(String tenantId, String factoryId, String deviceCode) {
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findByDeviceCode(tenantId, deviceCode)
                .orElseThrow(() -> new ServiceException(
                        com.weili.basic.common.enums.ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "设备不存在"));
        
        // 验证设备是否属于指定工厂
        if (factoryId != null && !factoryId.equals(device.getFactoryId())) {
            throw new ServiceException(
                    com.weili.basic.common.enums.ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                    "设备不属于指定工厂");
        }
        
        return convertToCommonVO(device);
    }

    @Override
    public List<DeviceBaseInfoVO> getDevicesByIds(String tenantId, String factoryId, List<String> deviceIds) {
        return deviceIds.stream()
                .map(deviceId -> {
                    try {
                        return getDeviceById(tenantId, factoryId, deviceId);
                    } catch (Exception e) {
                        // 如果某个设备不存在或不属于工厂，返回null，后续可以过滤
                        return null;
                    }
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<DeviceBaseInfoVO> getDevicesByFactory(String tenantId, String factoryId, String workshopId) {
        List<DeviceBaseInfoDO> devices = deviceBaseInfoRepository.findByFactoryId(tenantId, factoryId);
        
        // 如果指定了车间，则过滤车间设备
        if (workshopId != null && !workshopId.isEmpty()) {
            devices = devices.stream()
                    .filter(device -> workshopId.equals(device.getWorkshopId()))
                    .collect(Collectors.toList());
        }
        
        return devices.stream()
                .map(this::convertToCommonVO)
                .collect(Collectors.toList());
    }

    @Override
    public DeviceBaseInfoVO getDeviceByTbDeviceId(String tenantId, String tbDeviceId) {
        DeviceBaseInfoDO device = deviceBaseInfoRepository.findByTbDeviceId(tenantId, tbDeviceId)
                .orElseThrow(() -> new ServiceException(
                        com.weili.basic.common.enums.ErrorCodeConstants.DEFAULT_ERROR.getCode(),
                        "设备不存在"));
        
        return convertToCommonVO(device);
    }

    /**
     * 将内部DO转换为公共VO
     */
    private DeviceBaseInfoVO convertToCommonVO(DeviceBaseInfoDO device) {
        var builder = DeviceBaseInfoVO.builder()
                .id(device.getId())
                .tbDeviceId(device.getTbDeviceId())
                .deviceCode(device.getDeviceCode())
                .deviceName(device.getDeviceName())
                .deviceTypeId(device.getDeviceTypeId())
                .deviceTypeName(device.getDeviceTypeName())
                .deviceSubTypeName(device.getDeviceSubTypeName())
                .deviceModelId(device.getDeviceModelId())
                .modelName(device.getModelName())
                .manufacturer(device.getManufacturer())
                .factoryId(device.getFactoryId())
                .factoryName(device.getFactoryName())
                .workshopId(device.getWorkshopId())
                .workshopName(device.getWorkshopName())
                .productionLineId(device.getProductionLineId())
                .productionLineName(device.getProductionLineName())
                .deviceStatus(device.getDeviceStatus())
                .isMonitored(device.getIsMonitored());

        deviceConfigurationRepository.findByDeviceId(device.getTenantId(), device.getId())
                .ifPresent(config -> builder
                        .ipAddress(config.getIpAddress())
                        .macAddress(config.getMacAddress()));

        return builder.build();
    }
}

