package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceParamConfigDO;
import com.weili.iot_portal.dal.repository.device.DeviceParamConfigRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceParamConfigMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备参数仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceParamConfigRepositoryImpl implements DeviceParamConfigRepository {

    private final DeviceParamConfigMapper deviceParamConfigMapper;

    @Override
    public List<DeviceParamConfigDO> selectCurrent(String deviceId) {
        return deviceParamConfigMapper.selectList(baseWrapper(deviceId)
                .eq(DeviceParamConfigDO::getIsActive, Boolean.TRUE)
                .isNull(DeviceParamConfigDO::getEffectiveEndTs)
                .orderByDesc(DeviceParamConfigDO::getEffectiveStartTs));
    }

    @Override
    public List<DeviceParamConfigDO> selectHistory(String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceParamConfigDO> wrapper = baseWrapper(deviceId)
                .orderByDesc(DeviceParamConfigDO::getEffectiveStartTs)
                .orderByAsc(DeviceParamConfigDO::getParameterType);
        if (startTs != null) {
            wrapper.ge(DeviceParamConfigDO::getEffectiveStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceParamConfigDO::getEffectiveStartTs, endTs);
        }
        return deviceParamConfigMapper.selectList(wrapper);
    }

    @Override
    public void expireCurrent(String deviceId, String parameterType, long endTs) {
        LambdaUpdateWrapper<DeviceParamConfigDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DeviceParamConfigDO::getDeviceInfoId, deviceId)
                .eq(DeviceParamConfigDO::getParameterType, parameterType)
                .isNull(DeviceParamConfigDO::getEffectiveEndTs)
                .set(DeviceParamConfigDO::getEffectiveEndTs, endTs)
                .set(DeviceParamConfigDO::getIsActive, Boolean.FALSE);
        deviceParamConfigMapper.update(null, updateWrapper);
    }

    @Override
    public void insert(DeviceParamConfigDO entity) {
        deviceParamConfigMapper.insert(entity);
    }

    /**
     * 构建基础查询条件（对应 device_param_config 表的字段）
     */
    private LambdaQueryWrapper<DeviceParamConfigDO> baseWrapper(String deviceId) {
        LambdaQueryWrapper<DeviceParamConfigDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceParamConfigDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


