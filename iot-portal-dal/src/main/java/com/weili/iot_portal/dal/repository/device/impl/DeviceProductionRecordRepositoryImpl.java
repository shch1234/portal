package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.mapper.device.DeviceProductionRecordMapper;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeviceProductionRecordRepositoryImpl implements DeviceProductionRecordRepository {

    private final DeviceProductionRecordMapper mapper;

    @Override
    public Optional<DeviceProductionRecordDO> findLatestOngoing(Long deviceId) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .isNull(DeviceProductionRecordDO::getEndTs)
                .orderByDesc(DeviceProductionRecordDO::getStartTs)
                .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public void insert(DeviceProductionRecordDO record) {
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceProductionRecordDO record) {
        if (record == null || record.getId() == null) {
            return;
        }
        mapper.updateById(record);
    }

    @Override
    public List<DeviceProductionRecordDO> findByShift(Long deviceId, Integer shiftCode, LocalDate shiftDate) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .eq(DeviceProductionRecordDO::getShiftCode, shiftCode)
                .eq(DeviceProductionRecordDO::getShiftDate, shiftDate);
        return mapper.selectList(wrapper);
    }

    @Override
    public long countCompletedInRange(Long deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .isNotNull(DeviceProductionRecordDO::getEndTs);
        if (startTs != null) {
            wrapper.ge(DeviceProductionRecordDO::getEndTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceProductionRecordDO::getEndTs, endTs);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public List<Long> findDistinctDeviceIdsWithProductionRecords(long startTsSeconds, long endTsSeconds) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(DeviceProductionRecordDO::getDeviceInfoId)
                .isNotNull(DeviceProductionRecordDO::getEndTs)
                .ge(DeviceProductionRecordDO::getEndTs, startTsSeconds)
                .le(DeviceProductionRecordDO::getEndTs, endTsSeconds);
        return mapper.selectList(wrapper).stream()
                .map(DeviceProductionRecordDO::getDeviceInfoId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}


