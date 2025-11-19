package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceParameterDO;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.DeviceParameterMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceParameterRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备参数仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceParameterRepositoryImpl implements DeviceParameterRepository {

    private final DeviceParameterMapper deviceParameterMapper;

    @Override
    public List<DeviceParameterDO> selectCurrent(String tenantId, String deviceId) {
        return deviceParameterMapper.selectList(baseWrapper(tenantId, deviceId)
                .eq(DeviceParameterDO::getIsActive, Boolean.TRUE)
                .isNull(DeviceParameterDO::getEffectiveEndTs)
                .orderByDesc(DeviceParameterDO::getEffectiveStartTs));
    }

    @Override
    public List<DeviceParameterDO> selectHistory(String tenantId, String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceParameterDO> wrapper = baseWrapper(tenantId, deviceId)
                .orderByDesc(DeviceParameterDO::getEffectiveStartTs)
                .orderByAsc(DeviceParameterDO::getParameterType);
        if (startTs != null) {
            wrapper.ge(DeviceParameterDO::getEffectiveStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceParameterDO::getEffectiveStartTs, endTs);
        }
        return deviceParameterMapper.selectList(wrapper);
    }

    @Override
    public void expireCurrent(String tenantId, String deviceId, String parameterType, long endTs) {
        LambdaUpdateWrapper<DeviceParameterDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DeviceParameterDO::getTenantId, tenantId)
                .eq(DeviceParameterDO::getDeviceId, deviceId)
                .eq(DeviceParameterDO::getParameterType, parameterType)
                .isNull(DeviceParameterDO::getEffectiveEndTs)
                .set(DeviceParameterDO::getEffectiveEndTs, endTs)
                .set(DeviceParameterDO::getIsActive, Boolean.FALSE);
        deviceParameterMapper.update(null, updateWrapper);
    }

    @Override
    public void insert(DeviceParameterDO entity) {
        deviceParameterMapper.insert(entity);
    }

    private LambdaQueryWrapper<DeviceParameterDO> baseWrapper(String tenantId, String deviceId) {
        LambdaQueryWrapper<DeviceParameterDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(DeviceParameterDO::getTenantId, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceParameterDO::getDeviceId, deviceId);
        }
        return wrapper;
    }
}


