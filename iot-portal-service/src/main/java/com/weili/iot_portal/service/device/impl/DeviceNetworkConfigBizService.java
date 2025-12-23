package com.weili.iot_portal.service.device.impl;

import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceNetworkConfigRepository;
import com.weili.iot_portal.domain.device.req.DeviceNetworkConfigPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceNetworkConfigSaveReqVO;
import com.weili.iot_portal.service.device.IDeviceNetworkConfigBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 设备网络配置业务服务实现
 */
@Service
public class DeviceNetworkConfigBizService implements IDeviceNetworkConfigBizService {

    @Resource
    private DeviceNetworkConfigRepository deviceNetworkConfigRepository;

    @Resource
    private DeviceInfoRepository deviceInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDeviceNetworkConfig(DeviceNetworkConfigSaveReqVO createReqVO) {
        // 验证设备信息存在
        validateDeviceInfoExists(createReqVO.getDeviceInfoId());
        // 验证设备是否已存在网络配置
        Optional<DeviceNetworkConfigDO> existing = deviceNetworkConfigRepository.findByDeviceInfoId(createReqVO.getDeviceInfoId());
        if (existing.isPresent()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NETWORK_CONFIG_DUPLICATE);
        }

        DeviceNetworkConfigDO deviceNetworkConfig = BeanUtils.toBean(createReqVO, DeviceNetworkConfigDO.class);
        // 如果未设置是否生效，默认为true
        if (deviceNetworkConfig.getIsActive() == null) {
            deviceNetworkConfig.setIsActive(true);
        }
        deviceNetworkConfigRepository.insert(deviceNetworkConfig);
        return deviceNetworkConfig.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceNetworkConfig(DeviceNetworkConfigSaveReqVO updateReqVO) {
        // 验证设备网络配置存在
        if (updateReqVO.getId() == null) {
            validateDeviceNetworkConfigExists(updateReqVO.getId());
        } else {
            // 如果没有ID，通过设备ID查找
            Optional<DeviceNetworkConfigDO> existing = deviceNetworkConfigRepository.findByDeviceInfoId(updateReqVO.getDeviceInfoId());
            if (existing.isEmpty()) {
                throw new IotPortalException(IotPortalErrorCode.DEVICE_NETWORK_CONFIG_NOT_FOUND);
            }
            updateReqVO.setId(existing.get().getId());
        }
        // 验证设备信息存在
        validateDeviceInfoExists(updateReqVO.getDeviceInfoId());

        DeviceNetworkConfigDO deviceNetworkConfig = BeanUtils.toBean(updateReqVO, DeviceNetworkConfigDO.class);
        deviceNetworkConfigRepository.update(deviceNetworkConfig);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceNetworkConfig(Long id) {
        validateDeviceNetworkConfigExists(id);
        deviceNetworkConfigRepository.deleteById(id);
    }

    @Override
    public DeviceNetworkConfigDO getDeviceNetworkConfig(Long id) {
        return validateDeviceNetworkConfigExists(id);
    }

    @Override
    public DeviceNetworkConfigDO getDeviceNetworkConfigByDeviceId(Long deviceInfoId) {
        if (deviceInfoId == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceNetworkConfigDO> deviceNetworkConfig = deviceNetworkConfigRepository.findByDeviceInfoId(deviceInfoId);
        if (deviceNetworkConfig.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NETWORK_CONFIG_NOT_FOUND);
        }
        return deviceNetworkConfig.get();
    }

    @Override
    public PageResult<DeviceNetworkConfigDO> getDeviceNetworkConfigPage(DeviceNetworkConfigPageReqVO pageReqVO) {
        return deviceNetworkConfigRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceNetworkConfigPageQuery.class));
    }

    /**
     * 验证设备网络配置存在
     */
    private DeviceNetworkConfigDO validateDeviceNetworkConfigExists(Long id) {
        if (id == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceNetworkConfigDO> deviceNetworkConfig = deviceNetworkConfigRepository.findById(id);
        if (deviceNetworkConfig.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_NETWORK_CONFIG_NOT_FOUND);
        }
        return deviceNetworkConfig.get();
    }

    /**
     * 验证设备信息存在
     */
    private void validateDeviceInfoExists(Long deviceInfoId) {
        if (deviceInfoId != null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceInfoDO> deviceInfo = deviceInfoRepository.findById(deviceInfoId);
        if (deviceInfo.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
    }
}




