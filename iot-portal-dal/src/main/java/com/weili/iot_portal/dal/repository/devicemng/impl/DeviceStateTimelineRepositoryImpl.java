package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateTimelineMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备状态时间线仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceStateTimelineRepositoryImpl implements DeviceStateTimelineRepository {

    private final DeviceStateTimelineMapper deviceStateTimelineMapper;

    @Override
    public List<DeviceStateTimelineDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceStateTimelineDO> wrapper = baseQuery(tenantId, deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceStateTimelineDO::getStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceStateTimelineDO::getStartTs, endTs);
        }
        wrapper.orderByAsc(DeviceStateTimelineDO::getStartTs);
        return deviceStateTimelineMapper.selectList(wrapper);
    }

    @Override
    public List<DeviceStateTimelineDO> selectRecent(String tenantId, String deviceId, Long startTs, int limit) {
        LambdaQueryWrapper<DeviceStateTimelineDO> wrapper = baseQuery(tenantId, deviceId)
                .ge(startTs != null, DeviceStateTimelineDO::getStartTs, startTs)
                .orderByDesc(DeviceStateTimelineDO::getStartTs)
                .last("limit " + limit);
        List<DeviceStateTimelineDO> records = deviceStateTimelineMapper.selectList(wrapper);
        records.sort((o1, o2) -> Long.compare(o1.getStartTs(), o2.getStartTs()));
        return records;
    }

    @Override
    public Optional<DeviceStateTimelineDO> findLatestState(String tenantId, String deviceId) {
        LambdaQueryWrapper<DeviceStateTimelineDO> wrapper = baseQuery(tenantId, deviceId);
        
        // 优先查询进行中的状态（end_ts IS NULL）
        wrapper.isNull(DeviceStateTimelineDO::getEndTs)
                .orderByDesc(DeviceStateTimelineDO::getStartTs)
                .last("limit 1");
        
        DeviceStateTimelineDO record = deviceStateTimelineMapper.selectOne(wrapper);
        if (record != null) {
            return Optional.of(record);
        }
        
        // 如果没有进行中的状态，查询最近结束的状态
        wrapper = baseQuery(tenantId, deviceId);
        wrapper.isNotNull(DeviceStateTimelineDO::getEndTs)
                .orderByDesc(DeviceStateTimelineDO::getEndTs)
                .orderByDesc(DeviceStateTimelineDO::getStartTs)
                .last("limit 1");
        
        record = deviceStateTimelineMapper.selectOne(wrapper);
        return Optional.ofNullable(record);
    }

    /**
     * 构建基础查询条件（对应 device_state_record 表的字段）
     */
    private LambdaQueryWrapper<DeviceStateTimelineDO> baseQuery(String tenantId, String deviceId) {
        LambdaQueryWrapper<DeviceStateTimelineDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(DeviceStateTimelineDO::getTenantUuid, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceStateTimelineDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


