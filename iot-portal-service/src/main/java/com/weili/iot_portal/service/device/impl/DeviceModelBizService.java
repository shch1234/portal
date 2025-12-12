package com.weili.iot_portal.service.device.impl;

import cn.hutool.core.util.StrUtil;
import com.weili.basic.common.model.PageResult;
import com.weili.basic.common.util.BeanUtils;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.dal.ddd.device.DeviceModelPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceModelRepository;
import com.weili.iot_portal.domain.device.req.DeviceModelPageReqVO;
import com.weili.iot_portal.domain.device.req.DeviceModelSaveReqVO;
import com.weili.iot_portal.service.device.IDeviceModelBizService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 设备型号业务服务实现
 */
@Service
public class DeviceModelBizService implements IDeviceModelBizService {

    @Resource
    private DeviceModelRepository deviceModelRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDeviceModel(DeviceModelSaveReqVO createReqVO) {
        // 验证型号编码唯一性
        validateModelCodeUnique(null, createReqVO.getModelCode());

        DeviceModelDO deviceModel = BeanUtils.toBean(createReqVO, DeviceModelDO.class);
        deviceModelRepository.insert(deviceModel);
        return deviceModel.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDeviceModel(DeviceModelSaveReqVO updateReqVO) {
        // 验证设备型号存在
        validateDeviceModelExists(updateReqVO.getId());
        // 验证型号编码唯一性
        validateModelCodeUnique(updateReqVO.getId(), updateReqVO.getModelCode());

        DeviceModelDO deviceModel = BeanUtils.toBean(updateReqVO, DeviceModelDO.class);
        deviceModelRepository.update(deviceModel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceModel(String id) {
        validateDeviceModelExists(id);
        deviceModelRepository.deleteById(id);
    }

    @Override
    public DeviceModelDO getDeviceModel(String id) {
        return validateDeviceModelExists(id);
    }

    @Override
    public PageResult<DeviceModelDO> getDeviceModelPage(DeviceModelPageReqVO pageReqVO) {
        return deviceModelRepository.selectPage(BeanUtils.toBean(pageReqVO, DeviceModelPageQuery.class));
    }

    @Override
    public List<DeviceModelDO> getDeviceModelList() {
        // 这里需要Repository提供查询所有的方法，暂时返回空列表
        // 如果需要，可以在Repository中添加findAll方法
        return List.of();
    }

    /**
     * 验证设备型号存在
     */
    private DeviceModelDO validateDeviceModelExists(String id) {
        if (StrUtil.isBlank(id)) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_ID_EMPTY);
        }
        Optional<DeviceModelDO> deviceModel = deviceModelRepository.findById(id);
        if (deviceModel.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_MODEL_NOT_FOUND);
        }
        return deviceModel.get();
    }

    /**
     * 验证型号编码唯一性
     */
    private void validateModelCodeUnique(String id, String modelCode) {
        if (StrUtil.isBlank(modelCode)) {
            return;
        }
        boolean exists = deviceModelRepository.existsByModelCode(modelCode, id);
        if (exists) {
            throw new IotPortalException(IotPortalErrorCode.DEVICE_MODEL_CODE_DUPLICATE);
        }
    }
}




