package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceToolRecordMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DeviceToolRecordRepositoryImpl implements DeviceToolRecordRepository {

    private final DeviceToolRecordMapper mapper;

    @Override
    public void insertBatch(List<DeviceToolRecordDO> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.forEach(item -> {
            if (StringUtils.isBlank(item.getId())) {
                item.setId(UUID.randomUUID().toString());
            }
        });
        list.forEach(mapper::insert);
    }

    @Override
    public void insert(DeviceToolRecordDO record) {
        if (record == null) {
            return;
        }
        if (StringUtils.isBlank(record.getId())) {
            record.setId(UUID.randomUUID().toString());
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceToolRecordDO record) {
        if (record == null || StringUtils.isBlank(record.getId())) {
            return;
        }
        mapper.updateById(record);
    }

    /**
     * 按时间范围查询刀具使用记录（对应 device_tool_record 表的字段）
     */
    @Override
    public List<DeviceToolRecordDO> selectByRange(String deviceId, Long startTs, Long endTs, Integer limit) {
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
    public DeviceToolRecordDO findLatestOngoing(String deviceId) {
        LambdaQueryWrapper<DeviceToolRecordDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolRecordDO::getDeviceInfoId, deviceId)
                .isNull(DeviceToolRecordDO::getEndTs)
                .orderByDesc(DeviceToolRecordDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }
}

