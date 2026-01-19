package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.enums.UnitTypeEnum;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceOrgRelationPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceOrgRelationRepository;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationSaveReqVO;
import com.weili.iot_portal.domain.device.resp.DeviceOrgRelationSubRespVO;
import com.weili.iot_portal.service.device.IDeviceOrgRelationBizService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备组织单元业务服务实现
 */
@Service
public class DeviceOrgRelationBizService implements IDeviceOrgRelationBizService {

    @Resource
    private DeviceOrgRelationRepository deviceOrgRelationRepository;

    @Resource
    private DeviceInfoRepository deviceInfoRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDeviceOrgRelation(DeviceOrgRelationSaveReqVO createReqVO) {
        // 验证组织单元编码唯一性
        validateUnitCodeUnique(null, createReqVO.getUnitCode());
        // 如果存在父级，验证父级存在并处理父级ID
        Long orgParentId = null;
        if (StrUtil.isNotBlank(createReqVO.getOrgParentId())) {
            List<String> ids = Arrays.stream(createReqVO.getOrgParentId().split(",")).toList();
            ids.forEach(this::validateDeviceOrgRelationExists);
            // 确定最终的父级ID（如果有两个ID，取第二个；否则取第一个）
            String finalParentIdStr = ids.size() == 2 ? ids.get(1) : ids.get(0);
            orgParentId = Long.parseLong(finalParentIdStr);
            createReqVO.setOrgParentId(finalParentIdStr);
        }
        DeviceOrgRelationDO deviceOrgRelation = BeanUtils.toBean(createReqVO, DeviceOrgRelationDO.class);
        // 构建层级路径
        buildPath(deviceOrgRelation);
        deviceOrgRelation.setLevelNo(UnitTypeEnum.ofLevelNo(createReqVO.getUnitTypeValue()));
        // 设置父级ID（Long类型）
        deviceOrgRelation.setOrgParentId(orgParentId);
        deviceOrgRelationRepository.insert(deviceOrgRelation);
        return deviceOrgRelation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceOrgRelation(DeviceOrgRelationSaveReqVO updateReqVO) {
        // 验证组织单元存在
        validateDeviceOrgRelationExists(updateReqVO.getId());
        // 验证组织单元编码唯一性
        validateUnitCodeUnique(updateReqVO.getId(), updateReqVO.getUnitCode());
        // 如果存在父级，验证父级存在且不能是自己
        Long orgParentId = null;
        if (StrUtil.isNotBlank(updateReqVO.getOrgParentId())) {
            if (updateReqVO.getOrgParentId().equals(updateReqVO.getId())) {
                throw new IotPortalException(IotPortalErrorCode.DEFAULT_ERROR, "父级组织不能是自己");
            }
            List<String> ids = Arrays.stream(updateReqVO.getOrgParentId().split(",")).toList();
            ids.forEach(this::validateDeviceOrgRelationExists);
            // 确定最终的父级ID（如果有两个ID，取第二个；否则取第一个）
            String finalParentIdStr = ids.size() == 2 ? ids.get(1) : ids.get(0);
            orgParentId = Long.parseLong(finalParentIdStr);
            updateReqVO.setOrgParentId(finalParentIdStr);
        }

        DeviceOrgRelationDO deviceOrgRelation = BeanUtils.toBean(updateReqVO, DeviceOrgRelationDO.class);
        deviceOrgRelation.setLevelNo(UnitTypeEnum.ofLevelNo(updateReqVO.getUnitTypeValue()));
        // 设置父级ID（Long类型）
        deviceOrgRelation.setOrgParentId(orgParentId);
        // 构建层级路径
        buildPath(deviceOrgRelation);
        deviceOrgRelationRepository.update(deviceOrgRelation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceOrgRelation(String id) {
        validateDeviceOrgRelationExists(id);
        // 检查是否存在子组织单元
        Long parentId = Long.parseLong(id);
        List<DeviceOrgRelationDO> children = deviceOrgRelationRepository.findByParentId(parentId);
        if (!children.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ORG_HAS_CHILDREN);
        }
        // 检查是否有关联的设备
        List<DeviceInfoDO> devices = deviceInfoRepository.findByFactoryId(id);
        if (!devices.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ORG_HAS_DEVICES);
        }
        deviceOrgRelationRepository.deleteById(id);
    }

    @Override
    public DeviceOrgRelationDO getDeviceOrgRelation(String id) {
        return validateDeviceOrgRelationExists(id);
    }

    @Override
    public PageResult<DeviceOrgRelationDO> getDeviceOrgRelationPage(DeviceOrgRelationPageReqVO pageReqVO) {
        return deviceOrgRelationRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceOrgRelationPageQuery.class));
    }

    @Override
    public Map<Long, DeviceOrgRelationDO> listByIds(List<Long> ids) {
        List<DeviceOrgRelationDO> relationList = deviceOrgRelationRepository.listByIds(ids);
        if (CollectionUtils.isNotEmpty(relationList)) {
            return relationList.stream().collect(Collectors.toMap(DeviceOrgRelationDO::getId, deviceOrgRelationDO -> deviceOrgRelationDO));
        }
        return Collections.emptyMap();
    }

    @Override
    public List<DeviceOrgRelationSubRespVO> getOrgRelationCascadeTree() {
        // 获取所有启用的组织单元
        List<DeviceOrgRelationDO> allOrgRelations = deviceOrgRelationRepository.findAllActive();
        if (CollectionUtils.isEmpty(allOrgRelations)) {
            return Collections.emptyList();
        }

        // 按类型分组
        Map<String, List<DeviceOrgRelationDO>> typeMap = allOrgRelations.stream()
                .collect(Collectors.groupingBy(DeviceOrgRelationDO::getUnitTypeValue));

        // 获取工厂、车间、产线列表
        List<DeviceOrgRelationDO> factories = typeMap.getOrDefault(UnitTypeEnum.FACTORY.getCode(), Collections.emptyList());
        List<DeviceOrgRelationDO> workshops = typeMap.getOrDefault(UnitTypeEnum.WORKSHOP.getCode(), Collections.emptyList());
        List<DeviceOrgRelationDO> productionLines = typeMap.getOrDefault(UnitTypeEnum.PRODUCTION_LINE.getCode(), Collections.emptyList());

        // 按父级ID分组车间和产线
        Map<Long, List<DeviceOrgRelationDO>> workshopsByFactoryId = workshops.stream()
                .filter(w -> w.getOrgParentId() != null)
                .collect(Collectors.groupingBy(DeviceOrgRelationDO::getOrgParentId));

        Map<Long, List<DeviceOrgRelationDO>> productionLinesByWorkshopId = productionLines.stream()
                .filter(p -> p.getOrgParentId() != null)
                .collect(Collectors.groupingBy(DeviceOrgRelationDO::getOrgParentId));

        // 构建三层结构
        List<DeviceOrgRelationSubRespVO> result = new ArrayList<>();
        for (DeviceOrgRelationDO factory : factories) {
            DeviceOrgRelationSubRespVO factoryVO = new DeviceOrgRelationSubRespVO();
            factoryVO.setId(String.valueOf(factory.getId()));
            factoryVO.setUnitCode(factory.getUnitCode());
            factoryVO.setUnitName(factory.getUnitName());

            // 获取该工厂下的车间列表
            List<DeviceOrgRelationDO> factoryWorkshops = workshopsByFactoryId.getOrDefault(factory.getId(), Collections.emptyList());
            List<DeviceOrgRelationSubRespVO.WorkshopVO> workshopVOs = new ArrayList<>();

            for (DeviceOrgRelationDO workshop : factoryWorkshops) {
                DeviceOrgRelationSubRespVO.WorkshopVO workshopVO = new DeviceOrgRelationSubRespVO.WorkshopVO();
                workshopVO.setId(String.valueOf(workshop.getId()));
                workshopVO.setUnitCode(workshop.getUnitCode());
                workshopVO.setUnitName(workshop.getUnitName());

                // 获取该车间下的产线列表
                List<DeviceOrgRelationDO> workshopProductionLines = productionLinesByWorkshopId.getOrDefault(workshop.getId(), Collections.emptyList());
                List<DeviceOrgRelationSubRespVO.ProductionLineVO> productionLineVOs = new ArrayList<>();

                for (DeviceOrgRelationDO productionLine : workshopProductionLines) {
                    DeviceOrgRelationSubRespVO.ProductionLineVO productionLineVO = new DeviceOrgRelationSubRespVO.ProductionLineVO();
                    productionLineVO.setId(String.valueOf(productionLine.getId()));
                    productionLineVO.setUnitCode(productionLine.getUnitCode());
                    productionLineVO.setUnitName(productionLine.getUnitName());
                    productionLineVOs.add(productionLineVO);
                }

                workshopVO.setProductionLines(productionLineVOs);
                workshopVOs.add(workshopVO);
            }

            factoryVO.setWorkshops(workshopVOs);
            result.add(factoryVO);
        }

        return result;
    }

    /**
     * 验证设备组织单元存在
     */
    private DeviceOrgRelationDO validateDeviceOrgRelationExists(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceOrgRelationDO> deviceOrgRelation = deviceOrgRelationRepository.findById(id);
        if (deviceOrgRelation.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ORG_NOT_FOUND);
        }
        return deviceOrgRelation.get();
    }

    /**
     * 验证组织单元编码唯一性
     */
    private void validateUnitCodeUnique(String id, String unitCode) {
        if (StrUtil.isBlank(unitCode)) {
            return;
        }
        boolean exists = deviceOrgRelationRepository.existsByUnitCode(unitCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ORG_CODE_DUPLICATE);
        }
    }

    /**
     * 构建层级路径
     */
    private void buildPath(DeviceOrgRelationDO deviceOrgRelation) {
        if (deviceOrgRelation.getOrgParentId() == null) {
            // 根节点，路径就是自己的编码
            deviceOrgRelation.setPath("/" + deviceOrgRelation.getUnitCode());
        } else {
            // 获取父级路径，拼接自己的编码
            Optional<DeviceOrgRelationDO> parent = deviceOrgRelationRepository.findById(String.valueOf(deviceOrgRelation.getOrgParentId()));
            if (parent.isPresent()) {
                String parentPath = parent.get().getPath();
                deviceOrgRelation.setPath(parentPath + "/" + deviceOrgRelation.getUnitCode());
            } else {
                deviceOrgRelation.setPath("/" + deviceOrgRelation.getUnitCode());
            }
        }
    }
}

