package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.mapper.device.DeviceAlarmHistoryMapper;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.domain.device.resp.AlarmManageRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceAlarmHistoryRepositoryImpl implements DeviceAlarmHistoryRepository {

    private final DeviceAlarmHistoryMapper mapper;

    @Override
    public List<DeviceAlarmHistoryDO> findActiveByDevice(Long factoryId, Long deviceId) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId)
                .eq(factoryId != null, DeviceAlarmHistoryDO::getOrgFactoryId, factoryId)
                .eq(DeviceAlarmHistoryDO::getIsActive, 1);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<DeviceAlarmHistoryDO> findByRange(Long factoryId, Long deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId)
                .eq(factoryId != null, DeviceAlarmHistoryDO::getOrgFactoryId, factoryId);
        if (startTs != null) {
            wrapper.ge(DeviceAlarmHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceAlarmHistoryDO::getStartTs, endTs);
        }
        wrapper.orderByDesc(DeviceAlarmHistoryDO::getStartTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<DeviceAlarmHistoryDO> findByRangeWithPage(Long deviceId, Long startTs, Long endTs, Integer offset, Integer limit) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceAlarmHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceAlarmHistoryDO::getStartTs, endTs);
        }
        wrapper.orderByDesc(DeviceAlarmHistoryDO::getStartTs)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return mapper.selectList(wrapper);
    }

    @Override
    public Long countByRange(Long deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceAlarmHistoryDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceAlarmHistoryDO::getStartTs, endTs);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public Long countAlarmManageList(String deviceCode, String deviceType, Integer isActive, Long startTime, Long endTime) {
        return mapper.countAlarmManageList(deviceCode, deviceType, isActive, startTime, endTime);
    }

    @Override
    public List<AlarmManageRespVO> selectAlarmManageList(String deviceCode, String deviceType, Integer isActive,
                                                          Long startTime, Long endTime, Integer offset, Integer limit) {
        return mapper.selectAlarmManageList(deviceCode, deviceType, isActive, startTime, endTime, offset, limit);
    }

    @Override
    public void insert(DeviceAlarmHistoryDO record) {
        if (record == null) {
            return;
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceAlarmHistoryDO record) {
        if (record == null || record.getId() == null) {
            return;
        }
        mapper.updateById(record);
    }
}


