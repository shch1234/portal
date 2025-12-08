package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceParameterDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceParameterRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceParameterMapper;
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
        updateWrapper.eq(DeviceParameterDO::getTenantUuid, tenantId)
                .eq(DeviceParameterDO::getDeviceInfoId, deviceId)
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

    /**
     * 构建基础查询条件（对应 device_param_config 表的字段）
     */
    private LambdaQueryWrapper<DeviceParameterDO> baseWrapper(String tenantId, String deviceId) {
        LambdaQueryWrapper<DeviceParameterDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(DeviceParameterDO::getTenantUuid, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceParameterDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


