package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.*;
import com.weili.iot_portal.dal.ddd.device.DeviceAlarmHistoryQuery;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.device.*;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private DeviceParamConfigRepository deviceParamConfigRepository;

    @Resource
    private IDeviceTypeRelationBizService deviceTypeRelationBizService;

    @Resource
    private IDeviceOrgRelationBizService deviceOrgRelationBizService;

    @Resource
    private IDeviceModelBizService deviceModelBizService;

    @Resource
    private DeviceAlarmHistoryRepository deviceAlarmHistoryRepository;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDeviceInfo(DeviceInfoSaveReqVO createReqVO) {
        // 验证设备编号唯一性
        validateDeviceCodeUnique(null, createReqVO.getDeviceCode());
        // 验证设备型号存在
        validateDeviceModelExists(createReqVO.getDeviceModelId());

        // 创建设备基本信息
        DeviceInfoDO deviceInfo = BeanUtils.toBean(createReqVO, DeviceInfoDO.class);
        deviceInfoRepository.insert(deviceInfo);
        Long deviceInfoId = deviceInfo.getId();

        // 创建设备位置信息
        if (createReqVO.getLocation() != null) {
            DeviceLocationDO deviceLocation = DeviceInfoAssembler.createDeviceLocation(deviceInfoId, createReqVO);
            deviceLocationRepository.insert(deviceLocation);
        }

        // 创建设备网络配置
        if (createReqVO.getNetwork() != null) {
            DeviceNetworkConfigDO networkConfig = DeviceInfoAssembler.createNetworkConfigDO(deviceInfoId, createReqVO);
            deviceNetworkConfigRepository.insert(networkConfig);
        }

        // 创建设备参数配置（首次新增）
        if (createReqVO.getParamConfig() != null && !createReqVO.getParamConfig().isEmpty()) {
            // 验证：确保一个设备只包含一个 parameter_type 类型的数据
            validateParameterTypeUnique(createReqVO.getParamConfig());

            long now = Instant.now().getEpochSecond();
            for (DeviceParamConfigReq paramConfigReq : createReqVO.getParamConfig()) {
                // 验证参数类型不为空
                if (StrUtil.isBlank(paramConfigReq.getParameterType())) {
                    throw new IotPortalException(IotPortalErrorCode.DEVICE_PARAM_TYPE_EMPTY);
                }
                DeviceParamConfigDO paramConfig = DeviceInfoAssembler.createNewVersionParamConfig(
                        deviceInfoId, paramConfigReq, now);
                deviceParamConfigRepository.insert(paramConfig);
            }
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
        // 验证设备型号存在
        validateDeviceModelExists(updateReqVO.getDeviceModelId());

        // 更新设备基本信息
        DeviceInfoDO deviceInfo = BeanUtils.toBean(updateReqVO, DeviceInfoDO.class);
        deviceInfo.setId(existingDevice.getId());
        deviceInfoRepository.update(deviceInfo);

        // 更新设备位置信息
        if (updateReqVO.getLocation() != null) {
            Optional<DeviceLocationDO> existing = deviceLocationRepository.findByDeviceId(updateReqVO.getId());
            DeviceLocationDO deviceLocation = DeviceInfoAssembler.updateDeviceLocation(updateReqVO.getId(), existing.orElse(null), updateReqVO);
            if (existing.isPresent()) {
                deviceLocationRepository.update(deviceLocation);
            } else {
                deviceLocationRepository.insert(deviceLocation);
            }
        }

        // 更新设备网络配置
        if (updateReqVO.getNetwork() != null) {
            Optional<DeviceNetworkConfigDO> existing = deviceNetworkConfigRepository.findByDeviceInfoId(updateReqVO.getId());
            DeviceNetworkConfigDO deviceNetworkConfig = DeviceInfoAssembler.updateNetworkConfigDO(updateReqVO.getId(),
                    existing.orElse(null), updateReqVO);
            if (existing.isPresent()) {
                deviceNetworkConfigRepository.update(deviceNetworkConfig);
            } else {
                deviceNetworkConfigRepository.insert(deviceNetworkConfig);
            }
        }

        // 更新设备参数配置（支持历史版本）
        if (updateReqVO.getParamConfig() != null) {
            updateDeviceParamConfig(updateReqVO.getId(), updateReqVO.getParamConfig());
        }
    }

    /**
     * 更新设备参数配置（支持历史版本管理）
     * 如果参数发生变更，则：
     * 1. 将当前生效的记录的结束时间设置为当前时间
     * 2. 新增一条记录，生效开始时间为当前时间
     *
     * @param deviceInfoId    设备ID
     * @param paramConfigList 参数配置列表，一个设备只能包含一个相同 parameter_type 类型的数据
     */
    private void updateDeviceParamConfig(Long deviceInfoId, List<DeviceParamConfigReq> paramConfigList) {
        // 验证参数列表不为空
        if (paramConfigList == null || paramConfigList.isEmpty()) {
            return;
        }

        // 验证：确保一个设备只包含一个 parameter_type 类型的数据
        validateParameterTypeUnique(paramConfigList);

        // 查询当前生效的参数配置
        List<DeviceParamConfigDO> currentConfigs = deviceParamConfigRepository.selectCurrent(deviceInfoId);
        long now = Instant.now().getEpochSecond();

        // 遍历处理每个参数配置
        for (DeviceParamConfigReq paramConfigReq : paramConfigList) {
            // 验证参数类型不为空
            if (StrUtil.isBlank(paramConfigReq.getParameterType())) {
                throw new IotPortalException(IotPortalErrorCode.DEVICE_PARAM_TYPE_EMPTY);
            }

            // 查找当前参数类型的配置
            Optional<DeviceParamConfigDO> currentConfig = currentConfigs.stream()
                    .filter(config -> config.getParameterType().equals(paramConfigReq.getParameterType()))
                    .findFirst();

            if (currentConfig.isPresent()) {
                DeviceParamConfigDO existing = currentConfig.get();
                // 检查参数值是否发生变化
                boolean valueChanged = existing.getParameterValue() == null
                        || existing.getParameterValue().compareTo(paramConfigReq.getParameterValue()) != 0;

                if (valueChanged) {
                    // 参数值发生变化，需要创建新版本
                    // 1. 将当前记录的生效结束时间设置为当前时间
                    deviceParamConfigRepository.expireCurrent(deviceInfoId, paramConfigReq.getParameterType(), now);

                    // 2. 新增一条记录，生效开始时间为当前时间
                    DeviceParamConfigDO newConfig = DeviceInfoAssembler.createNewVersionParamConfig(
                            deviceInfoId, paramConfigReq, now);
                    deviceParamConfigRepository.insert(newConfig);
                }
                // 如果参数值没有变化，则不做任何操作
            } else {
                // 当前不存在该参数类型的配置，直接新增
                DeviceParamConfigDO newConfig = DeviceInfoAssembler.createNewVersionParamConfig(
                        deviceInfoId, paramConfigReq, now);
                deviceParamConfigRepository.insert(newConfig);
            }
        }
    }

    /**
     * 验证参数类型的唯一性
     * 确保传入的参数配置列表中，每个 parameter_type 只出现一次
     *
     * @param paramConfigList 参数配置列表
     * @throws IotPortalException 如果发现重复的 parameter_type
     */
    private void validateParameterTypeUnique(List<DeviceParamConfigReq> paramConfigList) {
        // 使用 Set 来检测重复的 parameter_type
        Set<String> parameterTypes = new HashSet<>();
        List<String> duplicateTypes = new ArrayList<>();

        for (DeviceParamConfigReq config : paramConfigList) {
            String parameterType = config.getParameterType();
            if (StrUtil.isNotBlank(parameterType)) {
                if (!parameterTypes.add(parameterType)) {
                    // 如果 add 返回 false，说明该类型已存在
                    duplicateTypes.add(parameterType);
                }
            }
        }

        // 如果发现重复的参数类型，抛出异常
        if (!duplicateTypes.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_PARAM_TYPE_DUPLICATE,
                    "参数类型重复: " + String.join(", ", duplicateTypes));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceInfo(Long id) {
        validateDeviceInfoExists(id);
        deviceInfoRepository.deleteById(id);
    }

    @Override
    public DeviceInfoDO getDeviceInfo(Long id) {
        return validateDeviceInfoExists(id);
    }

    @Override
    public DeviceInfoRespVO getDeviceInfoWithDetails(Long id) {
        DeviceInfoDO deviceInfo = validateDeviceInfoExists(id);
        DeviceInfoRespVO respVO = BeanUtils.toBean(deviceInfo, DeviceInfoRespVO.class);

        // 查询设备位置信息
        Optional<DeviceLocationDO> deviceLocation = deviceLocationRepository.findByDeviceId(id);
        if (deviceLocation.isPresent()) {
            DeviceLocationInfo locationInfo = BeanUtils.toBean(deviceLocation.get(), DeviceLocationInfo.class);
            respVO.setLocation(locationInfo);
        }

        // 查询设备网络配置
        Optional<DeviceNetworkConfigDO> deviceNetworkConfig = deviceNetworkConfigRepository.findByDeviceInfoId(id);
        if (deviceNetworkConfig.isPresent()) {
            DeviceNetworkInfo networkInfo = BeanUtils.toBean(deviceNetworkConfig.get(), DeviceNetworkInfo.class);
            respVO.setNetwork(networkInfo);
        }

        // 组装设备详细信息
        assembleDeviceInfoDetails(respVO);
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
        DeviceBaseInfoPageQuery pageQuery = BeanUtils.toBean(pageReqVO, DeviceBaseInfoPageQuery.class);
        if (StringUtils.isNotEmpty(pageReqVO.getDeviceCode())) {
            pageQuery.setDeviceCode(pageReqVO.getDeviceCode());
        }
        if (StringUtils.isNotBlank(pageReqVO.getDeviceStatus())) {
            pageQuery.setDeviceStatuses(Collections.singletonList(pageReqVO.getDeviceStatus()));
        }
        if (StringUtils.isNotBlank(pageReqVO.getDeviceSubTypeCode())) {
            pageQuery.setDeviceTypeCodes(Collections.singletonList(pageReqVO.getDeviceSubTypeCode()));
        } else if (StringUtils.isNotBlank(pageReqVO.getDeviceTypeCode())) {
            //查询子的
            List<DeviceTypeRelationDO> typeRelationList = deviceTypeRelationBizService.getDeviceTypeRelationByParentCode(pageReqVO.getDeviceTypeCode());
            if (typeRelationList.isEmpty()) {
                return PageResult.empty();
            }
            pageQuery.setDeviceTypeCodes(typeRelationList.stream().map(DeviceTypeRelationDO::getTypeCode).distinct().collect(Collectors.toList()));
        }

        if (StringUtils.isNotBlank(pageReqVO.getOrgFactoryId())) {
            pageQuery.setOrgFactoryIds(Collections.singletonList(pageReqVO.getOrgFactoryId()));
        }
        PageResult<DeviceInfoDO> pageResult = deviceInfoRepository.selectPage(pageQuery);
        PageResult<DeviceInfoRespVO> result = BeanUtils.toBean(pageResult, DeviceInfoRespVO.class);
        
        // 批量查询设备的未结束报警状态
        Set<Long> deviceIdsWithAlarm = getDeviceIdsWithActiveAlarm(pageResult.getList());
        
        // 组装设备详细信息并设置报警状态
        List<DeviceInfoDO> deviceDOList = pageResult.getList();
        List<DeviceInfoRespVO> deviceVOList = result.getList();
        for (int i = 0; i < deviceVOList.size(); i++) {
            DeviceInfoRespVO row = deviceVOList.get(i);
            // 组装设备详细信息
            assembleDeviceInfoDetails(row);
            // 设置是否报警字段
            if (i < deviceDOList.size()) {
                Long deviceId = deviceDOList.get(i).getId();
                row.setHasAlarm(deviceIdsWithAlarm.contains(deviceId));
            } else {
                row.setHasAlarm(false);
            }
        }
        return result;
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

    @Override
    public List<DeviceParamConfigReq> getDeviceParamConfig(Long id) {
        List<DeviceParamConfigDO> configList = deviceParamConfigRepository.selectCurrent(id);
        return BeanUtils.toBean(configList, DeviceParamConfigReq.class);
    }


    /**
     * 验证设备信息存在
     */
    private DeviceInfoDO validateDeviceInfoExists(Long id) {
        if (id == null) {
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
    private void validateDeviceCodeUnique(Long id, String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return;
        }
        boolean exists = deviceInfoRepository.existsByDeviceCode(deviceCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_CODE_DUPLICATE);
        }
    }

    /**
     * 验证设备型号存在
     */
    private void validateDeviceModelExists(Long deviceModelId) {
        if (deviceModelId == null) {
            return;
        }
        Optional<DeviceModelDO> deviceModel = deviceModelRepository.findById(deviceModelId);
        if (deviceModel.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
    }


    /**
     * 组装设备信息的详细信息（类型、型号、组织等）
     */
    private void assembleDeviceInfoDetails(DeviceInfoRespVO respVO) {
        // 组装设备类型信息
        assembleDeviceTypeInfo(respVO);

        // 组装设备型号信息
        assembleDeviceModelInfo(respVO);

        // 组装组织关系信息
        assembleDeviceOrgInfo(respVO);
    }

    /**
     * 组装设备类型信息
     */
    private void assembleDeviceTypeInfo(DeviceInfoRespVO respVO) {
        DeviceTypeRelationDO relationDO = deviceTypeRelationBizService.getDeviceTypeRelationByCode(respVO.getDeviceTypeCode());
        if (relationDO != null) {
            respVO.setDeviceSubTypeName(relationDO.getDescription());
            Long parentTypeId = relationDO.getParentTypeId();
            if (parentTypeId != null) {
                DeviceTypeRelationDO parentRelDO = deviceTypeRelationBizService.getDeviceTypeRelation(parentTypeId);
                if (parentRelDO != null) {
                    respVO.setDeviceTypeName(parentRelDO.getDescription());
                }
            }
        }
    }

    /**
     * 组装设备型号信息
     */
    private void assembleDeviceModelInfo(DeviceInfoRespVO respVO) {
        if (respVO.getDeviceModelId() != null) {
            DeviceModelDO deviceModel = deviceModelBizService.getDeviceModel(respVO.getDeviceModelId());
            if (deviceModel != null) {
                respVO.setDeviceModelName(deviceModel.getModelName());
            }
        }
    }

    /**
     * 组装组织关系信息
     */
    private void assembleDeviceOrgInfo(DeviceInfoRespVO respVO) {
        Set<Long> orgRelationIds = Stream.of(
                        respVO.getOrgFactoryId(),
                        respVO.getOrgWorkshopId(),
                        respVO.getOrgProductionLineId()
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!orgRelationIds.isEmpty()) {
            Map<Long, DeviceOrgRelationDO> orgRelationDOMap = deviceOrgRelationBizService.listByIds(new ArrayList<>(orgRelationIds));
            respVO.setFactoryName(getUnitName(orgRelationDOMap, respVO.getOrgFactoryId()));
            respVO.setWorkshopName(getUnitName(orgRelationDOMap, respVO.getOrgWorkshopId()));
            respVO.setProductionLineName(getUnitName(orgRelationDOMap, respVO.getOrgProductionLineId()));
        }
    }

    private String getUnitName(Map<Long, DeviceOrgRelationDO> orgRelationMap, Long orgId) {
        if (orgId == null) {
            return null;
        }
        DeviceOrgRelationDO orgRelation = orgRelationMap.get(orgId);
        return orgRelation != null ? orgRelation.getUnitName() : null;
    }

    /**
     * 批量查询有未结束报警的设备ID集合
     * 
     * @param deviceList 设备列表
     * @return 有未结束报警的设备ID集合
     */
    private Set<Long> getDeviceIdsWithActiveAlarm(List<DeviceInfoDO> deviceList) {
        if (deviceList == null || deviceList.isEmpty()) {
            return Collections.emptySet();
        }
        
        // 提取所有设备ID
        List<Long> deviceIds = deviceList.stream()
                .map(DeviceInfoDO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        if (deviceIds.isEmpty()) {
            return Collections.emptySet();
        }
        
        // 批量查询未结束的报警（isActive=1）
        DeviceAlarmHistoryQuery query = new DeviceAlarmHistoryQuery();
        query.setDeviceIds(deviceIds);
        query.setIsActive(1);
        query.setPageNo(1);
        query.setPageSize(10000); // 设置一个较大的值以获取所有结果
        
        PageResult<DeviceAlarmHistoryDO> alarmPageResult = deviceAlarmHistoryRepository.selectPage(query);
        
        // 提取有报警的设备ID（去重）
        return alarmPageResult.getList().stream()
                .map(DeviceAlarmHistoryDO::getDeviceInfoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

}

