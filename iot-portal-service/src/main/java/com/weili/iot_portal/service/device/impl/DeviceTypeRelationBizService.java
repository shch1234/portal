package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceTypeRelationRepository;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceTypeRelationSaveReqVO;
import com.weili.iot_portal.service.device.IDeviceTypeRelationBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 设备类型业务服务实现
 */
@Service
public class DeviceTypeRelationBizService implements IDeviceTypeRelationBizService {

    @Resource
    private DeviceTypeRelationRepository deviceTypeRelationRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDeviceTypeRelation(DeviceTypeRelationSaveReqVO createReqVO) {
        // 验证类型编码唯一性
        validateTypeCodeUnique(null, createReqVO.getTypeCode());
        // 如果存在父级，验证父级存在
        if (createReqVO.getParentTypeId() != null) {
            validateDeviceTypeRelationExists(createReqVO.getParentTypeId());
        }

        DeviceTypeRelationDO deviceTypeRelation = BeanUtils.toBean(createReqVO, DeviceTypeRelationDO.class);
        //处理path
        deviceTypeRelation.setPath(buildPath(deviceTypeRelation.getParentTypeId()) + "/" + deviceTypeRelation.getId());
        deviceTypeRelationRepository.insert(deviceTypeRelation);
        return deviceTypeRelation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceTypeRelation(DeviceTypeRelationSaveReqVO updateReqVO) {
        // 验证设备类型存在
        DeviceTypeRelationDO existing = validateDeviceTypeRelationExists(updateReqVO.getId());
        // 验证类型编码唯一性
        validateTypeCodeUnique(updateReqVO.getId(), updateReqVO.getTypeCode());
        // 如果存在父级，验证父级存在且不能是自己
        if (updateReqVO.getParentTypeId() != null) {
            if (updateReqVO.getParentTypeId().equals(updateReqVO.getId())) {
                throw new IotPortalException(IotPortalErrorCode.DEFAULT_ERROR, "父类型不能是自己");
            }
            validateDeviceTypeRelationExists(updateReqVO.getParentTypeId());
        }

        DeviceTypeRelationDO deviceTypeRelation = BeanUtils.toBean(updateReqVO, DeviceTypeRelationDO.class);
        if (!Objects.equals(existing.getParentTypeId(), updateReqVO.getParentTypeId())) {
            deviceTypeRelation.setPath(buildPath(deviceTypeRelation.getParentTypeId()) + "/" + deviceTypeRelation.getId());
            // 更新所有子节点的路径
            updateChildrenPaths(deviceTypeRelation.getId(), deviceTypeRelation.getPath());
        } else {
            // 父类型未变化，保持原有路径
            deviceTypeRelation.setPath(existing.getPath());
        }
        deviceTypeRelationRepository.update(deviceTypeRelation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceTypeRelation(Long id) {
        validateDeviceTypeRelationExists(id);
        // 检查是否存在子类型
        List<DeviceTypeRelationDO> children = deviceTypeRelationRepository.findByParentTypeId(id);
        if (!children.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TYPE_HAS_CHILDREN);
        }
        deviceTypeRelationRepository.deleteById(id);
    }

    @Override
    public DeviceTypeRelationDO getDeviceTypeRelation(Long id) {
        return validateDeviceTypeRelationExists(id);
    }

    @Override
    public DeviceTypeRelationDO getDeviceTypeRelationByCode(String typeCode) {
        Optional<DeviceTypeRelationDO> optional = deviceTypeRelationRepository.findByTypeCode(typeCode);
        return optional.orElse(null);
    }

    @Override
    public List<DeviceTypeRelationDO> getDeviceTypeRelationByParentCode(String typeCode) {
        return deviceTypeRelationRepository.findByParentTypeCode(typeCode);
    }

    @Override
    public PageResult<DeviceTypeRelationDO> getDeviceTypeRelationPage(DeviceTypeRelationPageReqVO pageReqVO) {
        return deviceTypeRelationRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceTypePageQuery.class));
    }

    /**
     * 验证设备类型存在
     */
    private DeviceTypeRelationDO validateDeviceTypeRelationExists(Long id) {
        if (id == null) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceTypeRelationDO> deviceTypeRelation = deviceTypeRelationRepository.findById(id);
        if (deviceTypeRelation.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TYPE_NOT_FOUND);
        }
        return deviceTypeRelation.get();
    }

    /**
     * 验证类型编码唯一性
     */
    private void validateTypeCodeUnique(Long id, String typeCode) {
        if (StrUtil.isBlank(typeCode)) {
            return;
        }
        boolean exists = deviceTypeRelationRepository.existsByTypeCode(typeCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TYPE_CODE_DUPLICATE);
        }
    }

    /**
     * 构建设备类型路径，递归获取父级路径
     */
    private String buildPath(Long parentTypeId) {
        if (parentTypeId == null) {
            return "";
        }

        DeviceTypeRelationDO parent = validateDeviceTypeRelationExists(parentTypeId);
        String parentPath = parent.getPath();
        if (parentPath == null) {
            // 如果父节点路径为空，递归构建
            parentPath = buildPath(parent.getParentTypeId()) + "/" + parent.getId();
            // 同时更新父节点的路径，确保路径一致性
            parent.setPath(parentPath);
            deviceTypeRelationRepository.update(parent);
        }
        return parentPath;
    }

    /**
     * 递归更新子节点的路径
     */
    private void updateChildrenPaths(Long parentId, String parentPath) {
        List<DeviceTypeRelationDO> children = deviceTypeRelationRepository.findByParentTypeId(parentId);
        for (DeviceTypeRelationDO child : children) {
            String newPath = parentPath + "/" + child.getId();
            child.setPath(newPath);
            deviceTypeRelationRepository.update(child);
            // 递归更新下级子节点
            updateChildrenPaths(child.getId(), newPath);
        }
    }
}




