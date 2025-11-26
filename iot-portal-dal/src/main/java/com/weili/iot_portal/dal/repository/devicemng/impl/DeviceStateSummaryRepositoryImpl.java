package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceStateSummaryRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceStateSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备状态汇总仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceStateSummaryRepositoryImpl implements DeviceStateSummaryRepository {

    private final DeviceStateSummaryMapper mapper;

    @Override
    public List<DeviceStateSummaryDO> selectByRange(String tenantId, String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tenantId)) {
            wrapper.eq(DeviceStateSummaryDO::getTenantId, tenantId);
        }
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceStateSummaryDO::getDeviceId, deviceId);
        }
        if (startTs != null) {
            wrapper.ge(DeviceStateSummaryDO::getShiftEndTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceStateSummaryDO::getShiftStartTs, endTs);
        }
        wrapper.orderByAsc(DeviceStateSummaryDO::getShiftStartTs);
        return mapper.selectList(wrapper);
    }
}


