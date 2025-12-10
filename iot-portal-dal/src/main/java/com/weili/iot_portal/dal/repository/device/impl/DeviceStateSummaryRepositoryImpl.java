package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateSummaryDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateSummaryRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceStateSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

/**
 * 设备状态汇总仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceStateSummaryRepositoryImpl implements DeviceStateSummaryRepository {

    private final DeviceStateSummaryMapper mapper;

    /**
     * 按时间范围查询设备状态汇总（对应 device_state_summary 表的字段）
     */
    @Override
    public List<DeviceStateSummaryDO> selectByRange(String deviceId, Long startTs, Long endTs) {
        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceStateSummaryDO::getDeviceInfoId, deviceId);
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

    @Override
    public List<DeviceStateSummaryDO> selectPending(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceStateSummaryDO::getIsFinalized, false)
                .between(DeviceStateSummaryDO::getSummaryDate, startDate, endDate)
                .orderByAsc(DeviceStateSummaryDO::getSummaryDate)
                .orderByAsc(DeviceStateSummaryDO::getShiftCode);
        return mapper.selectList(wrapper);
    }

    @Override
    public DeviceStateSummaryDO findByShift(String deviceId, LocalDate shiftDate, String shiftCode) {
        LambdaQueryWrapper<DeviceStateSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceStateSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceStateSummaryDO::getSummaryDate, shiftDate)
                .eq(DeviceStateSummaryDO::getShiftCode, shiftCode)
                .last("limit 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public void insert(DeviceStateSummaryDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceStateSummaryDO entity) {
        mapper.updateById(entity);
    }
}


