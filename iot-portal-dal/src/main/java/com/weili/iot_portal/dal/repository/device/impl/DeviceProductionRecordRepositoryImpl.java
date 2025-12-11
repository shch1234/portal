package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.mapper.device.DeviceProductionRecordMapper;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeviceProductionRecordRepositoryImpl implements DeviceProductionRecordRepository {

    private final DeviceProductionRecordMapper mapper;

    @Override
    public Optional<DeviceProductionRecordDO> findLatestOngoing(String deviceId) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .isNull(DeviceProductionRecordDO::getEndTs)
                .orderByDesc(DeviceProductionRecordDO::getStartTs)
                .last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public void insert(DeviceProductionRecordDO record) {
        if (record == null) {
            return;
        }
        if (StringUtils.isBlank(record.getId())) {
            record.setId(UUID.randomUUID().toString());
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceProductionRecordDO record) {
        if (record == null || StringUtils.isBlank(record.getId())) {
            return;
        }
        mapper.updateById(record);
    }

    @Override
    public List<DeviceProductionRecordDO> findByShift(String deviceId, String shiftCode, LocalDate shiftDate) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .eq(DeviceProductionRecordDO::getShiftCode, shiftCode)
                .eq(DeviceProductionRecordDO::getShiftDate, shiftDate);
        return mapper.selectList(wrapper);
    }

    @Override
    public long countCompletedInRange(String deviceId, Long startTs, Long endTs) {
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
}


