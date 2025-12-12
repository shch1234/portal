package com.weili.iot_portal.service.devicebase.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceTypeRelationRepository;
import com.weili.iot_portal.domain.devicebase.req.DeviceTypeRelationPageReqVO;
import com.weili.iot_portal.domain.devicebase.req.DeviceTypeRelationSaveReqVO;
import com.weili.iot_portal.service.devicebase.IDeviceTypeRelationBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public String createDeviceTypeRelation(DeviceTypeRelationSaveReqVO createReqVO) {
        // 验证类型编码唯一性
        validateTypeCodeUnique(null, createReqVO.getTypeCode());
        // 如果存在父级，验证父级存在
        if (StrUtil.isNotBlank(createReqVO.getParentTypeId())) {
            validateDeviceTypeRelationExists(createReqVO.getParentTypeId());
        }

        DeviceTypeRelationDO deviceTypeRelation = BeanUtils.toBean(createReqVO, DeviceTypeRelationDO.class);
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
        if (StrUtil.isNotBlank(updateReqVO.getParentTypeId())) {
            if (updateReqVO.getParentTypeId().equals(updateReqVO.getId())) {
                throw new IotPortalException(IotPortalErrorCode.DEFAULT_ERROR, "父类型不能是自己");
            }
            validateDeviceTypeRelationExists(updateReqVO.getParentTypeId());
        }

        DeviceTypeRelationDO deviceTypeRelation = BeanUtils.toBean(updateReqVO, DeviceTypeRelationDO.class);
        deviceTypeRelationRepository.update(deviceTypeRelation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceTypeRelation(String id) {
        DeviceTypeRelationDO deviceTypeRelation = validateDeviceTypeRelationExists(id);
        // 检查是否存在子类型
        List<DeviceTypeRelationDO> children = deviceTypeRelationRepository.findByParentTypeId(id);
        if (!children.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TYPE_HAS_CHILDREN);
        }
        deviceTypeRelationRepository.deleteById(id);
    }

    @Override
    public DeviceTypeRelationDO getDeviceTypeRelation(String id) {
        return validateDeviceTypeRelationExists(id);
    }

    @Override
    public List<DeviceTypeRelationDO> getDeviceTypeRelationByParentId(String parentTypeId) {
        return deviceTypeRelationRepository.findByParentTypeId(parentTypeId);
    }

    @Override
    public PageResult<DeviceTypeRelationDO> getDeviceTypeRelationPage(DeviceTypeRelationPageReqVO pageReqVO) {
        return deviceTypeRelationRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceTypePageQuery.class));
    }

    /**
     * 验证设备类型存在
     */
    private DeviceTypeRelationDO validateDeviceTypeRelationExists(String id) {
        if (StrUtil.isBlank(id)) {
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
    private void validateTypeCodeUnique(String id, String typeCode) {
        if (StrUtil.isBlank(typeCode)) {
            return;
        }
        boolean exists = deviceTypeRelationRepository.existsByTypeCode(typeCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_TYPE_CODE_DUPLICATE);
        }
    }
}



