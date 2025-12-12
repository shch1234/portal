package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceLocationRepository;
import com.weili.iot_portal.domain.device.req.DeviceLocationSaveReqVO;
import com.weili.iot_portal.service.device.IDeviceLocationBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 设备位置业务服务实现
 */
@Service
public class DeviceLocationBizService implements IDeviceLocationBizService {

    @Resource
    private DeviceLocationRepository deviceLocationRepository;

    @Resource
    private DeviceInfoRepository deviceInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDeviceLocation(DeviceLocationSaveReqVO createReqVO) {
        // 验证设备信息存在
        validateDeviceInfoExists(createReqVO.getDeviceInfoId());

        DeviceLocationDO deviceLocation = BeanUtils.toBean(createReqVO, DeviceLocationDO.class);
        // 如果未设置是否生效，默认为true
        if (deviceLocation.getActive() == null) {
            deviceLocation.setActive(true);
        }
        deviceLocationRepository.insert(deviceLocation);
        return deviceLocation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceLocation(DeviceLocationSaveReqVO updateReqVO) {
        // 验证设备位置存在（通过设备ID查找）
        Optional<DeviceLocationDO> existing = deviceLocationRepository.findByDeviceId(updateReqVO.getDeviceInfoId());
        if (existing.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_LOCATION_NOT_FOUND);
        }
        // 验证设备信息存在
        validateDeviceInfoExists(updateReqVO.getDeviceInfoId());

        DeviceLocationDO deviceLocation = BeanUtils.toBean(updateReqVO, DeviceLocationDO.class);
        deviceLocation.setId(existing.get().getId());
        deviceLocationRepository.update(deviceLocation);
    }

    @Override
    public DeviceLocationDO getDeviceLocationByDeviceId(String deviceInfoId) {
        if (StrUtil.isBlank(deviceInfoId)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceLocationDO> deviceLocation = deviceLocationRepository.findByDeviceId(deviceInfoId);
        if (deviceLocation.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_LOCATION_NOT_FOUND);
        }
        return deviceLocation.get();
    }

    @Override
    public List<DeviceLocationDO> getDeviceLocationsByDeviceIds(List<String> deviceInfoIds) {
        if (deviceInfoIds == null || deviceInfoIds.isEmpty()) {
            return List.of();
        }
        return deviceLocationRepository.findByDeviceIds(deviceInfoIds);
    }

    /**
     * 验证设备信息存在
     */
    private void validateDeviceInfoExists(String deviceInfoId) {
        if (StrUtil.isBlank(deviceInfoId)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceInfoDO> deviceInfo = deviceInfoRepository.findById(deviceInfoId);
        if (deviceInfo.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
    }
}




