package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.ShiftConfigurationDO;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.ShiftConfigurationMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.ShiftConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

    @Override
    public Optional<ShiftConfigurationDO> findActiveByDeviceAndTime(String tenantId, String deviceId, long timestamp) {
        LambdaQueryWrapper<ShiftConfigurationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftConfigurationDO::getTenantId, tenantId)
                .eq(ShiftConfigurationDO::getDeviceId, deviceId)
                .eq(ShiftConfigurationDO::getIsActive, Boolean.TRUE)
                .le(ShiftConfigurationDO::getEffectiveStartTs, timestamp)
                .and(w -> w.isNull(ShiftConfigurationDO::getEffectiveEndTs)
                        .or()
                        .ge(ShiftConfigurationDO::getEffectiveEndTs, timestamp))
                .orderByDesc(ShiftConfigurationDO::getEffectiveStartTs)
                .last("LIMIT 1");
        return Optional.ofNullable(shiftConfigurationMapper.selectOne(wrapper));
    }

    @Override
    public List<ShiftConfigurationDO> findByDeviceAndTimeRange(String tenantId, String deviceId, long startTs, long endTs) {
        LambdaQueryWrapper<ShiftConfigurationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ShiftConfigurationDO::getTenantId, tenantId)
                .eq(ShiftConfigurationDO::getDeviceId, deviceId)
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

    @Override
    public void updateEffectiveEndTs(String tenantId, String deviceId, long endTs) {
        LambdaUpdateWrapper<ShiftConfigurationDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ShiftConfigurationDO::getTenantId, tenantId)
                .eq(ShiftConfigurationDO::getDeviceId, deviceId)
                .eq(ShiftConfigurationDO::getIsActive, Boolean.TRUE)
                .isNull(ShiftConfigurationDO::getEffectiveEndTs)
                .set(ShiftConfigurationDO::getEffectiveEndTs, endTs)
                .set(ShiftConfigurationDO::getIsActive, Boolean.FALSE);
        shiftConfigurationMapper.update(null, updateWrapper);
    }
}

