package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.ddd.device.DeviceAlarmHistoryQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceAlarmHistoryMapper;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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
    public PageResult<DeviceAlarmHistoryDO> selectPage(DeviceAlarmHistoryQuery query) {
        Page<DeviceAlarmHistoryDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        if (query.getDeviceId()!= null) {
            wrapper.eq(DeviceAlarmHistoryDO::getDeviceInfoId, query.getDeviceId());
        }
        if (CollectionUtils.isNotEmpty(query.getDeviceIds())) {
            wrapper.in(DeviceAlarmHistoryDO::getDeviceInfoId, query.getDeviceIds());
        }
        if (query.getFactoryId() != null) {
            wrapper.eq(DeviceAlarmHistoryDO::getOrgFactoryId, query.getFactoryId());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceAlarmHistoryDO::getIsActive, query.getIsActive());
        }
        if (query.getStartTime() != null) {
            wrapper.eq(DeviceAlarmHistoryDO::getStartTs, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.eq(DeviceAlarmHistoryDO::getEndTs, query.getEndTime());
        }
        wrapper.orderByDesc(DeviceAlarmHistoryDO::getStartTs);
        Page<DeviceAlarmHistoryDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
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


