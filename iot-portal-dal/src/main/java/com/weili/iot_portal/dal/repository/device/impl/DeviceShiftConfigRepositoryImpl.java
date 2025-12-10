package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceShiftConfigDO;
import com.weili.iot_portal.dal.repository.device.DeviceShiftConfigRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceShiftConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 班次配置仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceShiftConfigRepositoryImpl implements DeviceShiftConfigRepository {

    private final DeviceShiftConfigMapper shiftConfigurationMapper;

    /**
     * 查找指定时间点的有效班次配置（对应 device_shift_config 表的字段）
     */
    @Override
    public Optional<DeviceShiftConfigDO> findActiveByDeviceAndTime(String deviceId, long timestamp) {
        LambdaQueryWrapper<DeviceShiftConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceShiftConfigDO::getDeviceInfoId, deviceId)
                .eq(DeviceShiftConfigDO::getIsActive, Boolean.TRUE)
                .le(DeviceShiftConfigDO::getEffectiveStartTs, timestamp)
                .and(w -> w.isNull(DeviceShiftConfigDO::getEffectiveEndTs)
                        .or()
                        .ge(DeviceShiftConfigDO::getEffectiveEndTs, timestamp))
                .orderByDesc(DeviceShiftConfigDO::getEffectiveStartTs)
                .last("LIMIT 1");
        return Optional.ofNullable(shiftConfigurationMapper.selectOne(wrapper));
    }

    /**
     * 查找时间范围内的班次配置（对应 device_shift_config 表的字段）
     */
    @Override
    public List<DeviceShiftConfigDO> findByDeviceAndTimeRange(String deviceId, long startTs, long endTs) {
        LambdaQueryWrapper<DeviceShiftConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceShiftConfigDO::getDeviceInfoId, deviceId)
                .le(DeviceShiftConfigDO::getEffectiveStartTs, endTs)
                .and(w -> w.isNull(DeviceShiftConfigDO::getEffectiveEndTs)
                        .or()
                        .ge(DeviceShiftConfigDO::getEffectiveEndTs, startTs))
                .orderByDesc(DeviceShiftConfigDO::getEffectiveStartTs);
        return shiftConfigurationMapper.selectList(wrapper);
    }

    @Override
    public void insert(DeviceShiftConfigDO entity) {
        shiftConfigurationMapper.insert(entity);
    }

    /**
     * 更新生效结束时间（对应 device_shift_config 表的字段）
     */
    @Override
    public void updateEffectiveEndTs(String deviceId, long endTs) {
        LambdaUpdateWrapper<DeviceShiftConfigDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DeviceShiftConfigDO::getDeviceInfoId, deviceId)
                .eq(DeviceShiftConfigDO::getIsActive, Boolean.TRUE)
                .isNull(DeviceShiftConfigDO::getEffectiveEndTs)
                .set(DeviceShiftConfigDO::getEffectiveEndTs, endTs)
                .set(DeviceShiftConfigDO::getIsActive, Boolean.FALSE);
        shiftConfigurationMapper.update(null, updateWrapper);
    }
}

