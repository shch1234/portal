package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceMetricSummaryDO;
import com.weili.iot_portal.dal.mapper.device.DeviceMetricSummaryMapper;
import com.weili.iot_portal.dal.repository.device.DeviceMetricSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 设备班次指标仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceMetricSummaryRepositoryImpl implements DeviceMetricSummaryRepository {

    private final DeviceMetricSummaryMapper mapper;

    @Override
    public Optional<DeviceMetricSummaryDO> selectLatestFinalized(Long deviceId) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = baseQuery(deviceId)
                .eq(DeviceMetricSummaryDO::getIsFinalized, Boolean.TRUE)
                .orderByDesc(DeviceMetricSummaryDO::getShiftStartTs)
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public PageResult<DeviceMetricSummaryDO> selectPage(Long deviceId,
                                                        Long startTs, Long endTs, int pageNo, int pageSize) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = baseQuery(deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceMetricSummaryDO::getShiftStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceMetricSummaryDO::getShiftEndTs, endTs);
        }
        wrapper.orderByDesc(DeviceMetricSummaryDO::getShiftStartTs);

        Page<DeviceMetricSummaryDO> page = new Page<>(pageNo, pageSize);
        Page<DeviceMetricSummaryDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public DeviceMetricSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMetricSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceMetricSummaryDO::getShiftDate, shiftDate)
                .eq(DeviceMetricSummaryDO::getShiftCode, shiftCode)
                .last("limit 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public java.util.List<DeviceMetricSummaryDO> selectFinalizedUpTo(Long deviceId, Long endTs) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMetricSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (endTs != null) {
            wrapper.le(DeviceMetricSummaryDO::getShiftEndTs, endTs);
        }
        wrapper.orderByAsc(DeviceMetricSummaryDO::getShiftEndTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public void insert(DeviceMetricSummaryDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceMetricSummaryDO entity) {
        mapper.updateById(entity);
    }

    /**
     * 构建基础查询条件（对应 device_metrics_summary 表的字段）
     */
    private LambdaQueryWrapper<DeviceMetricSummaryDO> baseQuery(Long deviceId) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DeviceMetricSummaryDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


