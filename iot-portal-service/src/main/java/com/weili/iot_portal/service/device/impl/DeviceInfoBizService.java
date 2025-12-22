package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.*;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceLocationRepository;
import com.weili.iot_portal.dal.repository.device.DeviceModelRepository;
import com.weili.iot_portal.dal.repository.device.DeviceNetworkConfigRepository;
import com.weili.iot_portal.domain.device.req.*;
import com.weili.iot_portal.domain.device.resp.*;
import com.weili.iot_portal.service.assembler.DeviceInfoAssembler;
import com.weili.iot_portal.service.device.IDeviceInfoBizService;
import com.weili.iot_portal.service.device.IDeviceModelBizService;
import com.weili.iot_portal.service.device.IDeviceOrgRelationBizService;
import com.weili.iot_portal.service.device.IDeviceTypeRelationBizService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Resource
    private IDeviceTypeRelationBizService deviceTypeRelationBizService;

    @Resource
    private IDeviceOrgRelationBizService deviceOrgRelationBizService;

    @Resource
    private IDeviceModelBizService deviceModelBizService;

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
            DeviceLocationDO deviceLocation = DeviceInfoAssembler.createDeviceLocation(deviceInfoId, createReqVO);
            deviceLocationRepository.insert(deviceLocation);
        }

        // 创建设备网络配置（如果提供）
        if (createReqVO.getNetwork() != null) {
            DeviceNetworkConfigDO networkConfig = DeviceInfoAssembler.createNetworkConfigDO(deviceInfoId, createReqVO);
            deviceNetworkConfigRepository.insert(networkConfig);
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
        deviceInfo.setId(existingDevice.getId());
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

        //组装数据
        DeviceTypeRelationDO relationDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(deviceInfo.getDeviceTypeCode());
        if (relationDO != null) {
            respVO.setDeviceSubTypeName(relationDO.getDescription());
            String parentTypeCode = relationDO.getParentTypeCode();
            DeviceTypeRelationDO parentRelDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(parentTypeCode);
            if (parentRelDO != null) {
                respVO.setDeviceTypeName(parentRelDO.getDescription());
            }
        }
        DeviceModelDO deviceModel = deviceModelBizService.getDeviceModel(deviceInfo.getDeviceModelId());
        if (deviceModel != null) {
            respVO.setDeviceModelName(deviceModel.getModelName());
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
    public PageResult<DeviceInfoRespVO> getDeviceInfoPage(DeviceInfoBasePageReqVO pageReqVO) {
        PageResult<DeviceInfoDO> pageResult = deviceInfoRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceBaseInfoPageQuery.class));
        PageResult<DeviceInfoRespVO> result = BeanUtils.toBean(pageResult, DeviceInfoRespVO.class);
        Map<String, String> deviceSubNameMap = new HashMap<>();
        Map<String, String> deviceTypeNameMap = new HashMap<>();
        Map<String, String> deviceModelNameMap = new HashMap<>();
        for (DeviceInfoRespVO row : result.getList()) {
            String deviceTypeName = "";
            String deviceSubTypeName = deviceSubNameMap.get(row.getDeviceTypeCode());
            if (StringUtils.isBlank(deviceSubTypeName)) {
                DeviceTypeRelationDO relationDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(row.getDeviceTypeCode());
                if (relationDO != null) {
                    deviceSubTypeName = relationDO.getDescription();
                    deviceSubNameMap.put(row.getDeviceTypeCode(), deviceSubTypeName);
                    deviceTypeName = deviceTypeNameMap.get(row.getDeviceTypeCode());
                    if (StringUtils.isBlank(deviceTypeName)) {
                        String parentTypeCode = relationDO.getParentTypeCode();
                        DeviceTypeRelationDO parentRelDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(parentTypeCode);
                        if (parentRelDO != null) {
                            deviceTypeName = parentRelDO.getDescription();
                            deviceTypeNameMap.put(row.getDeviceTypeCode(), deviceTypeName);
                        }
                    }
                }
            }
            row.setDeviceTypeName(deviceTypeName);
            row.setDeviceSubTypeName(deviceSubTypeName);
            String modelName = deviceModelNameMap.get(row.getDeviceModelId());
            if (StringUtils.isEmpty(modelName)) {
                DeviceModelDO deviceModel = deviceModelBizService.getDeviceModel(row.getDeviceModelId());
                if (deviceModel != null) {
                    modelName = deviceModel.getModelName();
                    deviceModelNameMap.put(row.getDeviceModelId(), modelName);
                }
            }
            row.setDeviceModelName(modelName);
        }
        return result;
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
     * 更新设备位置信息
     */
    private void updateDeviceLocation(String deviceInfoId, DeviceInfoSaveReqVO updateReqVO) {
        Optional<DeviceLocationDO> existing = deviceLocationRepository.findByDeviceId(deviceInfoId);
        DeviceLocationDO deviceLocation = DeviceInfoAssembler.updateDeviceLocation(deviceInfoId, existing.orElse(null), updateReqVO);
        if (existing.isPresent()) {
            deviceLocationRepository.update(deviceLocation);
        } else {
            deviceLocationRepository.insert(deviceLocation);
        }
    }


    /**
     * 更新设备网络配置
     */
    private void updateDeviceNetworkConfig(String deviceInfoId, DeviceInfoSaveReqVO updateReqVO) {
        Optional<DeviceNetworkConfigDO> existing = deviceNetworkConfigRepository.findByDeviceInfoId(deviceInfoId);
        DeviceNetworkConfigDO deviceNetworkConfig = DeviceInfoAssembler.updateNetworkConfigDO(deviceInfoId, existing.orElse(null), updateReqVO);
        if (existing.isPresent()) {
            deviceNetworkConfigRepository.update(deviceNetworkConfig);
        } else {
            deviceNetworkConfigRepository.insert(deviceNetworkConfig);
        }
    }

    @Override
    public DeviceInfoOptionsRespVO getDeviceInfoOptions() {
        DeviceInfoOptionsRespVO options = new DeviceInfoOptionsRespVO();

        // 获取设备类型列表（仅启用状态）
        DeviceTypeRelationPageReqVO typePageReq = new DeviceTypeRelationPageReqVO();
        typePageReq.setIsActive(true);
        typePageReq.setPageNo(1);
        typePageReq.setPageSize(10000); // 设置一个很大的值以获取所有数据
        typePageReq.setParentTypeId(null); //查询父的设备类型
        PageResult<DeviceTypeRelationDO> typePageResult = deviceTypeRelationBizService.getDeviceTypeRelationPage(typePageReq);
        List<DeviceTypeRelationRespVO> deviceTypes = BeanUtils.toBean(typePageResult.getList(), DeviceTypeRelationRespVO.class);
        //查询父的子类型
        for (DeviceTypeRelationRespVO relationType : deviceTypes) {
            String typeCode = relationType.getTypeCode();
            List<DeviceTypeRelationDO> subList = deviceTypeRelationBizService.getDeviceTypeRelationByParentCode(typeCode);
            relationType.setSubDeviceList(BeanUtils.toBean(subList, DeviceTypeRelationRespVO.class));
        }
        options.setDeviceTypes(deviceTypes);

        // 获取厂区列表（仅启用状态，层级1）
        DeviceOrgRelationPageReqVO factoryPageReq = new DeviceOrgRelationPageReqVO();
        factoryPageReq.setIsActive(true);
        factoryPageReq.setLevelNo(1);
        factoryPageReq.setPageNo(1);
        factoryPageReq.setPageSize(10000);
        PageResult<DeviceOrgRelationDO> factoryPageResult = deviceOrgRelationBizService.getDeviceOrgRelationPage(factoryPageReq);
        List<DeviceOrgRelationRespVO> factories = BeanUtils.toBean(factoryPageResult.getList(), DeviceOrgRelationRespVO.class);
        options.setFactories(factories);

        // 获取车间列表（仅启用状态，层级2）
        DeviceOrgRelationPageReqVO workshopPageReq = new DeviceOrgRelationPageReqVO();
        workshopPageReq.setIsActive(true);
        workshopPageReq.setLevelNo(2);
        workshopPageReq.setPageNo(1);
        workshopPageReq.setPageSize(10000);
        PageResult<DeviceOrgRelationDO> workshopPageResult = deviceOrgRelationBizService.getDeviceOrgRelationPage(workshopPageReq);
        List<DeviceOrgRelationRespVO> workshops = BeanUtils.toBean(workshopPageResult.getList(), DeviceOrgRelationRespVO.class);
        options.setWorkshops(workshops);

        // 获取产线列表（仅启用状态，层级3）
        DeviceOrgRelationPageReqVO productionLinePageReq = new DeviceOrgRelationPageReqVO();
        productionLinePageReq.setIsActive(true);
        productionLinePageReq.setLevelNo(3);
        productionLinePageReq.setPageNo(1);
        productionLinePageReq.setPageSize(10000);
        PageResult<DeviceOrgRelationDO> productionLinePageResult = deviceOrgRelationBizService.getDeviceOrgRelationPage(productionLinePageReq);
        List<DeviceOrgRelationRespVO> productionLines = BeanUtils.toBean(productionLinePageResult.getList(), DeviceOrgRelationRespVO.class);
        options.setProductionLines(productionLines);

        // 获取设备型号列表（仅启用状态）
        DeviceModelPageReqVO modelPageReq = new DeviceModelPageReqVO();
        modelPageReq.setIsActive(true);
        modelPageReq.setPageNo(1);
        modelPageReq.setPageSize(10000);
        PageResult<DeviceModelDO> modelPageResult = deviceModelBizService.getDeviceModelPage(modelPageReq);
        List<DeviceModelRespVO> deviceModels = BeanUtils.toBean(modelPageResult.getList(), DeviceModelRespVO.class);
        options.setDeviceModels(deviceModels);

        return options;
    }
}

