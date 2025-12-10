package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.mapper.device.DeviceAlarmHistoryMapper;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeviceAlarmHistoryRepositoryImpl implements DeviceAlarmHistoryRepository {

    private final DeviceAlarmHistoryMapper mapper;

    @Override
    public List<DeviceAlarmHistoryDO> findActiveByDevice(String factoryId, String deviceId) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId)
                .eq(StringUtils.isNotBlank(factoryId), DeviceAlarmHistoryDO::getOrgFactoryId, factoryId)
                .eq(DeviceAlarmHistoryDO::getIsActive, 1);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<DeviceAlarmHistoryDO> findByRange(String factoryId, String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId)
                .eq(StringUtils.isNotBlank(factoryId), DeviceAlarmHistoryDO::getOrgFactoryId, factoryId);
        if (startTs != null) {
            wrapper.ge(DeviceAlarmHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceAlarmHistoryDO::getStartTs, endTs);
        }
        wrapper.orderByAsc(DeviceAlarmHistoryDO::getStartTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public void insert(DeviceAlarmHistoryDO record) {
        if (record == null) {
            return;
        }
        if (StringUtils.isBlank(record.getId())) {
            record.setId(UUID.randomUUID().toString());
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceAlarmHistoryDO record) {
        if (record == null || StringUtils.isBlank(record.getId())) {
            return;
        }
        mapper.updateById(record);
    }
}


