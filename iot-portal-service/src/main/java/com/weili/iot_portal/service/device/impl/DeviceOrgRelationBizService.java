package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceOrgRelationPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceOrgRelationRepository;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceOrgRelationSaveReqVO;
import com.weili.iot_portal.service.device.IDeviceOrgRelationBizService;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // 如果存在父级，验证父级存在
        if (StrUtil.isNotBlank(createReqVO.getOrgParentId())) {
            validateDeviceOrgRelationExists(createReqVO.getOrgParentId());
        }

        DeviceOrgRelationDO deviceOrgRelation = BeanUtils.toBean(createReqVO, DeviceOrgRelationDO.class);
        // 构建层级路径
        buildPath(deviceOrgRelation);
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
        if (StrUtil.isNotBlank(updateReqVO.getOrgParentId())) {
            if (updateReqVO.getOrgParentId().equals(updateReqVO.getId())) {
                throw new IotPortalException(IotPortalErrorCode.DEFAULT_ERROR, "父级组织不能是自己");
            }
            validateDeviceOrgRelationExists(updateReqVO.getOrgParentId());
        }

        DeviceOrgRelationDO deviceOrgRelation = BeanUtils.toBean(updateReqVO, DeviceOrgRelationDO.class);
        // 构建层级路径
        buildPath(deviceOrgRelation);
        deviceOrgRelationRepository.update(deviceOrgRelation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceOrgRelation(String id) {
        validateDeviceOrgRelationExists(id);
        // 检查是否存在子组织单元
        List<DeviceOrgRelationDO> children = deviceOrgRelationRepository.findByParentId(id);
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
        if (StrUtil.isBlank(deviceOrgRelation.getOrgParentId())) {
            // 根节点，路径就是自己的编码
            deviceOrgRelation.setPath("/" + deviceOrgRelation.getUnitCode());
        } else {
            // 获取父级路径，拼接自己的编码
            Optional<DeviceOrgRelationDO> parent = deviceOrgRelationRepository.findById(deviceOrgRelation.getOrgParentId());
            if (parent.isPresent()) {
                String parentPath = parent.get().getPath();
                deviceOrgRelation.setPath(parentPath + "/" + deviceOrgRelation.getUnitCode());
            } else {
                deviceOrgRelation.setPath("/" + deviceOrgRelation.getUnitCode());
            }
        }
    }
}

