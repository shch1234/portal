package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceStateRecordMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 设备状态时间线仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceStateRecordRepositoryImpl implements DeviceStateRecordRepository {

    private final DeviceStateRecordMapper deviceStateRecordMapper;

    @Override
    public List<DeviceStateRecordDO> selectByRange(String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);
        if (!Objects.isNull(startTs)) {
            wrapper.ge(DeviceStateRecordDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceStateRecordDO::getStartTs, endTs);
        }
        wrapper.orderByAsc(DeviceStateRecordDO::getStartTs);
        return deviceStateRecordMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceStateRecordDO> selectRecent(String deviceId, Long startTs, int limit) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId)
                .ge(startTs != null, DeviceStateRecordDO::getStartTs, startTs)
                .orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit " + limit);
        List<DeviceStateRecordDO> records = deviceStateRecordMapper.selectList(wrapper);
        records.sort((o1, o2) -> Long.compare(o1.getStartTs(), o2.getStartTs()));
        return records;
    }

    @Override
    public Optional<DeviceStateRecordDO> findLatestState(String deviceId) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = baseQuery(deviceId);
        
        // 优先查询进行中的状态（end_ts IS NULL）
        wrapper.isNull(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit 1");
        
        DeviceStateRecordDO record = deviceStateRecordMapper.selectOne(wrapper);
        if (record != null) {
            return Optional.of(record);
        }
        
        // 如果没有进行中的状态，查询最近结束的状态
        wrapper.isNotNull(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getEndTs)
                .orderByDesc(DeviceStateRecordDO::getStartTs)
                .last("limit 1");
        
        record = deviceStateRecordMapper.selectOne(wrapper);
        return Optional.ofNullable(record);
    }

    /**
     * 构建基础查询条件（对应 device_state_record 表的字段）
     */
    private LambdaQueryWrapper<DeviceStateRecordDO> baseQuery(String deviceId) {
        LambdaQueryWrapper<DeviceStateRecordDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceStateRecordDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


