package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.ShiftConfigurationDO;
import com.weili.iot_portal.dal.repository.devicemng.ShiftConfigurationRepository;
import com.weili.iot_portal.dal.mapper.devicemng.ShiftConfigurationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 班次配置仓储实现
 */
@Repository
@RequiredArgsConstructor
public class ShiftConfigurationRepositoryImpl implements ShiftConfigurationRepository {

    private final ShiftConfigurationMapper shiftConfigurationMapper;

    /**
     * 查找指定时间点的有效班次配置（对应 device_shift_config 表的字段）
     */
    @Override
    public Optional<ShiftConfigurationDO> findActiveByDeviceAndTime(String deviceId, long timestamp) {
        LambdaQueryWrapper<ShiftConfigurationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftConfigurationDO::getDeviceInfoId, deviceId)
                .eq(ShiftConfigurationDO::getIsActive, Boolean.TRUE)
                .le(ShiftConfigurationDO::getEffectiveStartTs, timestamp)
                .and(w -> w.isNull(ShiftConfigurationDO::getEffectiveEndTs)
                        .or()
                        .ge(ShiftConfigurationDO::getEffectiveEndTs, timestamp))
                .orderByDesc(ShiftConfigurationDO::getEffectiveStartTs)
                .last("LIMIT 1");
        return Optional.ofNullable(shiftConfigurationMapper.selectOne(wrapper));
    }

    /**
     * 查找时间范围内的班次配置（对应 device_shift_config 表的字段）
     */
    @Override
    public List<ShiftConfigurationDO> findByDeviceAndTimeRange(String deviceId, long startTs, long endTs) {
        LambdaQueryWrapper<ShiftConfigurationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftConfigurationDO::getDeviceInfoId, deviceId)
                .le(ShiftConfigurationDO::getEffectiveStartTs, endTs)
                .and(w -> w.isNull(ShiftConfigurationDO::getEffectiveEndTs)
                        .or()
                        .ge(ShiftConfigurationDO::getEffectiveEndTs, startTs))
                .orderByDesc(ShiftConfigurationDO::getEffectiveStartTs);
        return shiftConfigurationMapper.selectList(wrapper);
    }

    @Override
    public void insert(ShiftConfigurationDO entity) {
        shiftConfigurationMapper.insert(entity);
    }

    /**
     * 更新生效结束时间（对应 device_shift_config 表的字段）
     */
    @Override
    public void updateEffectiveEndTs(String deviceId, long endTs) {
        LambdaUpdateWrapper<ShiftConfigurationDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ShiftConfigurationDO::getDeviceInfoId, deviceId)
                .eq(ShiftConfigurationDO::getIsActive, Boolean.TRUE)
                .isNull(ShiftConfigurationDO::getEffectiveEndTs)
                .set(ShiftConfigurationDO::getEffectiveEndTs, endTs)
                .set(ShiftConfigurationDO::getIsActive, Boolean.FALSE);
        shiftConfigurationMapper.update(null, updateWrapper);
    }
}

