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
import java.util.Objects;
import java.util.stream.Collectors;

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
    public List<Long> findDeviceIdsWithActiveAlarm(List<Long> deviceIds) {
        if (CollectionUtils.isEmpty(deviceIds)) {
            return List.of();
        }
        
        // 优化：只查询 deviceInfoId 字段，避免查询完整报警对象，减少数据传输和内存占用
        // 注意：MyBatis-Plus 的 LambdaQueryWrapper 不支持直接使用 DISTINCT，
        // 但通过 select() 只查询设备ID字段，然后在应用层去重，性能已经很好
        // 如果同一设备有多条未结束的报警，数据库会返回多条记录，应用层去重即可
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(DeviceAlarmHistoryDO::getDeviceInfoId)  // 只查询设备ID字段，不查询其他字段
                .in(DeviceAlarmHistoryDO::getDeviceInfoId, deviceIds)
                .eq(DeviceAlarmHistoryDO::getIsActive, 1);
        
        // 查询结果并在应用层去重（如果同一设备有多条报警，只保留一个设备ID）
        return mapper.selectList(wrapper).stream()
                .map(DeviceAlarmHistoryDO::getDeviceInfoId)
                .filter(Objects::nonNull)
                .distinct()  // 应用层去重，性能开销很小
                .collect(Collectors.toList());
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
    public List<DeviceAlarmHistoryDO> findTopByDuration(Long factoryId, Integer topN) {
        LambdaQueryWrapper<DeviceAlarmHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(factoryId != null, DeviceAlarmHistoryDO::getOrgFactoryId, factoryId)
                .isNotNull(DeviceAlarmHistoryDO::getDurationS)
                .orderByDesc(DeviceAlarmHistoryDO::getDurationS)
                .last("LIMIT " + topN);
        return mapper.selectList(wrapper);
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


