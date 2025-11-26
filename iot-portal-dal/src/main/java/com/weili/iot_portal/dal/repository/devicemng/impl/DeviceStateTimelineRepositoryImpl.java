package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateTimelineDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateTimelineRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateTimelineMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    private LambdaQueryWrapper<DeviceStateTimelineDO> baseQuery(String tenantId, String deviceId) {
        LambdaQueryWrapper<DeviceStateTimelineDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(DeviceStateTimelineDO::getTenantId, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceStateTimelineDO::getDeviceId, deviceId);
        }
        return wrapper;
    }
}


