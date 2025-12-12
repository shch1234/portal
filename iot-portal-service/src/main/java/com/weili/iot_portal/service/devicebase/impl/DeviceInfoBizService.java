package com.weili.iot_portal.service.devicebase.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.domain.devicebase.resp.DeviceInfoRespVO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceLocationRepository;
import com.weili.iot_portal.dal.repository.device.DeviceModelRepository;
import com.weili.iot_portal.dal.repository.device.DeviceNetworkConfigRepository;
import com.weili.iot_portal.domain.devicebase.req.DeviceInfoBasePageReqVO;
import com.weili.iot_portal.domain.devicebase.req.DeviceInfoSaveReqVO;
import com.weili.iot_portal.service.devicebase.IDeviceInfoBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 设备信息业务服务实现
 */
@Service
public class DeviceInfoBizService implements IDeviceInfoBizService {

    @Resource
    private DeviceInfoRepository deviceInfoRepository;

    @Resource
    private DeviceModelRepository deviceModelRepository;

    @Resource
    private DeviceLocationRepository deviceLocationRepository;

    @Resource
    private DeviceNetworkConfigRepository deviceNetworkConfigRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDeviceInfo(DeviceInfoSaveReqVO createReqVO) {
        // 验证设备编号唯一性
        validateDeviceCodeUnique(null, createReqVO.getDeviceCode());
        // 验证ThingsBoard设备ID唯一性
        validateTbDeviceIdUnique(null, createReqVO.getTbDeviceId());
        // 验证设备型号存在
        validateDeviceModelExists(createReqVO.getDeviceModelId());

        // 创建设备基本信息
        DeviceInfoDO deviceInfo = BeanUtils.toBean(createReqVO, DeviceInfoDO.class);
        deviceInfoRepository.insert(deviceInfo);
        String deviceInfoId = deviceInfo.getId();

        // 创建设备位置信息（如果提供）
        if (createReqVO.getLocation() != null) {
            createDeviceLocation(deviceInfoId, createReqVO);
        }

        // 创建设备网络配置（如果提供）
        if (createReqVO.getNetwork() != null) {
            createDeviceNetworkConfig(deviceInfoId, createReqVO);
        }

        return deviceInfoId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceInfo(DeviceInfoSaveReqVO updateReqVO) {
        // 验证设备信息存在
        DeviceInfoDO existingDevice = validateDeviceInfoExists(updateReqVO.getId());
        // 验证设备编号唯一性
        validateDeviceCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceCode());
        // 验证ThingsBoard设备ID唯一性
        validateTbDeviceIdUnique(updateReqVO.getId(), updateReqVO.getTbDeviceId());
        // 验证设备型号存在
        validateDeviceModelExists(updateReqVO.getDeviceModelId());

        // 更新设备基本信息
        DeviceInfoDO deviceInfo = BeanUtils.toBean(updateReqVO, DeviceInfoDO.class);
        deviceInfoRepository.update(deviceInfo);

        // 更新设备位置信息（如果提供）
        if (updateReqVO.getLocation() != null) {
            updateDeviceLocation(updateReqVO.getId(), updateReqVO);
        }

        // 更新设备网络配置（如果提供）
        if (updateReqVO.getNetwork() != null) {
            updateDeviceNetworkConfig(updateReqVO.getId(), updateReqVO);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceInfo(String id) {
        validateDeviceInfoExists(id);
        deviceInfoRepository.deleteById(id);
    }

    @Override
    public DeviceInfoDO getDeviceInfo(String id) {
        return validateDeviceInfoExists(id);
    }

    @Override
    public DeviceInfoRespVO getDeviceInfoWithDetails(String id) {
        DeviceInfoDO deviceInfo = validateDeviceInfoExists(id);
        DeviceInfoRespVO respVO = BeanUtils.toBean(deviceInfo, DeviceInfoRespVO.class);

        // 查询设备位置信息
        Optional<DeviceLocationDO> deviceLocation = deviceLocationRepository.findByDeviceId(id);
        if (deviceLocation.isPresent()) {
            DeviceInfoRespVO.DeviceLocationInfo locationInfo = BeanUtils.toBean(deviceLocation.get(), DeviceInfoRespVO.DeviceLocationInfo.class);
            respVO.setLocation(locationInfo);
        }

        // 查询设备网络配置
        Optional<DeviceNetworkConfigDO> deviceNetworkConfig = deviceNetworkConfigRepository.findByDeviceInfoId(id);
        if (deviceNetworkConfig.isPresent()) {
            DeviceInfoRespVO.DeviceNetworkInfo networkInfo = BeanUtils.toBean(deviceNetworkConfig.get(), DeviceInfoRespVO.DeviceNetworkInfo.class);
            respVO.setNetwork(networkInfo);
        }

        return respVO;
    }

    @Override
    public DeviceInfoDO getDeviceInfoByCode(String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_CODE_EMPTY);
        }
        Optional<DeviceInfoDO> deviceInfo = deviceInfoRepository.findByDeviceCode(deviceCode);
        if (deviceInfo.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        return deviceInfo.get();
    }

    @Override
    public PageResult<DeviceInfoDO> getDeviceInfoPage(DeviceInfoBasePageReqVO pageReqVO) {
        return deviceInfoRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceBaseInfoPageQuery.class));
    }

    @Override
    public List<DeviceInfoDO> getDeviceInfoList() {
        return deviceInfoRepository.findAllActive();
    }

    /**
     * 验证设备信息存在
     */
    private DeviceInfoDO validateDeviceInfoExists(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceInfoDO> deviceInfo = deviceInfoRepository.findById(id);
        if (deviceInfo.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_INFO_NOT_FOUND);
        }
        return deviceInfo.get();
    }

    /**
     * 验证设备编号唯一性
     */
    private void validateDeviceCodeUnique(String id, String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return;
        }
        boolean exists = deviceInfoRepository.existsByDeviceCode(deviceCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_CODE_DUPLICATE);
        }
    }

    /**
     * 验证ThingsBoard设备ID唯一性
     */
    private void validateTbDeviceIdUnique(String id, String tbDeviceId) {
        if (StrUtil.isBlank(tbDeviceId)) {
            return;
        }
        boolean exists = deviceInfoRepository.existsByTbDeviceId(tbDeviceId, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TB_DEVICE_ID_DUPLICATE);
        }
    }

    /**
     * 验证设备型号存在
     */
    private void validateDeviceModelExists(String deviceModelId) {
        if (StrUtil.isBlank(deviceModelId)) {
            return;
        }
        Optional<DeviceModelDO> deviceModel = deviceModelRepository.findById(deviceModelId);
        if (deviceModel.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
    }

    /**
     * 创建设备位置信息
     */
    private void createDeviceLocation(String deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
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
        deviceLocationRepository.insert(deviceLocation);
    }

    /**
     * 更新设备位置信息
     */
    private void updateDeviceLocation(String deviceInfoId, DeviceInfoSaveReqVO updateReqVO) {
        DeviceInfoSaveReqVO.DeviceLocationInfo locationInfo = updateReqVO.getLocation();
        Optional<DeviceLocationDO> existing = deviceLocationRepository.findByDeviceId(deviceInfoId);
        
        DeviceLocationDO deviceLocation;
        if (existing.isPresent()) {
            // 更新现有位置信息
            deviceLocation = existing.get();
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
        
        if (existing.isPresent()) {
            deviceLocationRepository.update(deviceLocation);
        } else {
            deviceLocationRepository.insert(deviceLocation);
        }
    }

    /**
     * 创建设备网络配置
     */
    private void createDeviceNetworkConfig(String deviceInfoId, DeviceInfoSaveReqVO createReqVO) {
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
        deviceNetworkConfigRepository.insert(deviceNetworkConfig);
    }

    /**
     * 更新设备网络配置
     */
    private void updateDeviceNetworkConfig(String deviceInfoId, DeviceInfoSaveReqVO updateReqVO) {
        DeviceInfoSaveReqVO.DeviceNetworkInfo networkInfo = updateReqVO.getNetwork();
        Optional<DeviceNetworkConfigDO> existing = deviceNetworkConfigRepository.findByDeviceInfoId(deviceInfoId);
        
        DeviceNetworkConfigDO deviceNetworkConfig;
        if (existing.isPresent()) {
            // 更新现有网络配置
            deviceNetworkConfig = existing.get();
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
        
        if (existing.isPresent()) {
            deviceNetworkConfigRepository.update(deviceNetworkConfig);
        } else {
            deviceNetworkConfigRepository.insert(deviceNetworkConfig);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceLocation(String deviceInfoId, DeviceInfoSaveReqVO.DeviceLocationInfo locationInfo) {
        validateDeviceInfoExists(deviceInfoId);
        DeviceInfoSaveReqVO reqVO = new DeviceInfoSaveReqVO();
        reqVO.setId(deviceInfoId);
        reqVO.setLocation(locationInfo);
        updateDeviceLocation(deviceInfoId, reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceNetworkConfig(String deviceInfoId, DeviceInfoSaveReqVO.DeviceNetworkInfo networkInfo) {
        validateDeviceInfoExists(deviceInfoId);
        DeviceInfoSaveReqVO reqVO = new DeviceInfoSaveReqVO();
        reqVO.setId(deviceInfoId);
        reqVO.setNetwork(networkInfo);
        updateDeviceNetworkConfig(deviceInfoId, reqVO);
    }
}

