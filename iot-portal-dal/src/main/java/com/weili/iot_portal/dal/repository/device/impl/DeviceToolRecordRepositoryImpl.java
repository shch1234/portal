package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolRecordMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceToolRecordRepositoryImpl implements DeviceToolRecordRepository {

    private final DeviceToolRecordMapper mapper;

    @Override
    public void insertBatch(List<DeviceToolRecordDO> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        mapper.insert(list);
    }

    @Override
    public void insert(DeviceToolRecordDO record) {
        if (record == null) {
            return;
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceToolRecordDO record) {
        if (record == null || record.getId() == null) {
            return;
        }
        mapper.updateById(record);
    }

    /**
     * 按时间范围查询刀具使用记录（对应 device_tool_record 表的字段）
     */
    @Override
    public List<DeviceToolRecordDO> selectByRange(Long deviceId, Long startTs, Long endTs, Integer limit) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceToolRecordDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceToolRecordDO::getEndTs, endTs);
        }
        wrapper.orderByDesc(DeviceToolRecordDO::getStartTs);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return mapper.selectList(wrapper);
    }

    @Override
    public List<DeviceToolRecordDO> selectByRangeWithPage(Long deviceId, Integer offset, Integer limit) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId)
                .orderByDesc(DeviceToolRecordDO::getStartTs)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return mapper.selectList(wrapper);
    }

    @Override
    public Long countByDevice(Long deviceId) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId);
        return mapper.selectCount(wrapper);
    }

    @Override
    public DeviceToolRecordDO findLatestOngoing(Long deviceId) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId)
                .isNull(DeviceToolRecordDO::getEndTs)
                .orderByDesc(DeviceToolRecordDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public List<DeviceToolRecordDO> findAllOngoingByToolNo(Long deviceId, String toolNo) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId)
                .eq(DeviceToolRecordDO::getToolNo, toolNo)
                .isNull(DeviceToolRecordDO::getEndTs)
                .orderByDesc(DeviceToolRecordDO::getStartTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public DeviceToolRecordDO findByDeviceIdAndToolNoAndTimeRange(Long deviceId, String toolNo, Long startTs, Long timeRangeMs) {
        if (deviceId == null || toolNo == null || startTs == null || timeRangeMs == null || timeRangeMs < 0) {
            return null;
        }
        
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId)
                .eq(DeviceToolRecordDO::getToolNo, toolNo)
                .ge(DeviceToolRecordDO::getStartTs, startTs - timeRangeMs)
                .le(DeviceToolRecordDO::getStartTs, startTs + timeRangeMs)
                .orderByDesc(DeviceToolRecordDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        mapper.deleteById(id);
    }
}

