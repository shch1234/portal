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
        // 将秒级时间戳转换为毫秒级时间戳（数据库中的 end_ts 是毫秒级）
        Long startTsMillis = startTs != null ? startTs * 1000 : null;
        Long endTsMillis = endTs != null ? endTs * 1000 : null;
        
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .isNotNull(DeviceProductionRecordDO::getEndTs);
        if (startTsMillis != null) {
            wrapper.ge(DeviceProductionRecordDO::getEndTs, startTsMillis);
        }
        if (endTsMillis != null) {
            wrapper.le(DeviceProductionRecordDO::getEndTs, endTsMillis);
        }
        return mapper.selectCount(wrapper);
    }

    @Override
    public long countByDate(Long deviceInfoId, LocalDate shiftDate) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceInfoId)
                .eq(DeviceProductionRecordDO::getShiftDate, shiftDate)
                .isNotNull(DeviceProductionRecordDO::getEndTs); // 只统计已完成的记录（end_ts不为null）
        return mapper.selectCount(wrapper);
    }

    @Override
    public List<Long> findDistinctDeviceIdsWithProductionRecords(long startTsSeconds, long endTsSeconds) {
        // 将秒级时间戳转换为毫秒级时间戳（数据库中的 end_ts 是毫秒级）
        long startTsMillis = startTsSeconds * 1000;
        long endTsMillis = endTsSeconds * 1000;
        
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(DeviceProductionRecordDO::getDeviceInfoId)
                .isNotNull(DeviceProductionRecordDO::getEndTs)
                .ge(DeviceProductionRecordDO::getEndTs, startTsMillis)
                .le(DeviceProductionRecordDO::getEndTs, endTsMillis);
        return mapper.selectList(wrapper).stream()
                .map(DeviceProductionRecordDO::getDeviceInfoId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Optional<Long> findLatestCompletedDurationS(Long deviceId) {
        LambdaQueryWrapper<DeviceProductionRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionRecordDO::getDeviceInfoId, deviceId)
                .isNotNull(DeviceProductionRecordDO::getEndTs)  // 必须是已完成的记录
                .isNotNull(DeviceProductionRecordDO::getDurationS)  // duration_s 不能为空
                .gt(DeviceProductionRecordDO::getDurationS, 0)  // duration_s 必须大于0
                .orderByDesc(DeviceProductionRecordDO::getEndTs)  // 按结束时间降序
                .last("LIMIT 1");
        DeviceProductionRecordDO record = mapper.selectOne(wrapper);
        return record != null && record.getDurationS() != null && record.getDurationS() > 0
                ? Optional.of(record.getDurationS())
                : Optional.empty();
    }
}


