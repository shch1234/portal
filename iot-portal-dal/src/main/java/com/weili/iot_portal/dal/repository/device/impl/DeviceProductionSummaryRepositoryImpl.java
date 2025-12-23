package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionSummaryDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceProductionSummaryMapper;
import com.weili.iot_portal.dal.repository.device.DeviceProductionSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 班次产量仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceProductionSummaryRepositoryImpl implements DeviceProductionSummaryRepository {

    private final DeviceProductionSummaryMapper deviceProductionSummaryMapper;

    /**
     * 查找当前班次的产量统计（对应 device_production_summary 表的字段）
     */
    @Override
    public Optional<DeviceProductionSummaryDO> findCurrent(Long deviceId, long currentTs) {
        LambdaQueryWrapper<DeviceProductionSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionSummaryDO::getDeviceInfoId, deviceId)
                .le(DeviceProductionSummaryDO::getShiftStartTs, currentTs)
                .ge(DeviceProductionSummaryDO::getShiftEndTs, currentTs)
                .orderByDesc(DeviceProductionSummaryDO::getShiftStartTs)
                .last("limit 1");
        DeviceProductionSummaryDO current = deviceProductionSummaryMapper.selectOne(wrapper);
        if (current != null) {
            return Optional.of(current);
        }
        // 如果当前没有进行中的班次，返回最近一班
        LambdaQueryWrapper<DeviceProductionSummaryDO> latestWrapper = new LambdaQueryWrapper<>();
        latestWrapper.eq(DeviceProductionSummaryDO::getDeviceInfoId, deviceId)
                .orderByDesc(DeviceProductionSummaryDO::getShiftStartTs)
                .last("limit 1");
        return Optional.ofNullable(deviceProductionSummaryMapper.selectOne(latestWrapper));
    }

    /**
     * 分页查询产量统计（对应 device_production_summary 表的字段）
     */
    @Override
    public PageResult<DeviceProductionSummaryDO> selectPage(ProductionCounterPageQuery query) {
        LambdaQueryWrapper<DeviceProductionSummaryDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getDeviceId())) {
            wrapper.eq(DeviceProductionSummaryDO::getDeviceInfoId, query.getDeviceId());
        }
        if (query.getStartTs() != null) {
            wrapper.ge(DeviceProductionSummaryDO::getShiftStartTs, query.getStartTs());
        }
        if (query.getEndTs() != null) {
            wrapper.le(DeviceProductionSummaryDO::getShiftEndTs, query.getEndTs());
        }
        boolean asc = "asc".equalsIgnoreCase(query.getSortDirection());
        if (StringUtils.isBlank(query.getSortBy()) || "shiftStartTs".equalsIgnoreCase(query.getSortBy())) {
            wrapper.orderBy(true, asc, DeviceProductionSummaryDO::getShiftStartTs);
        } else if ("shiftEndTs".equalsIgnoreCase(query.getSortBy())) {
            wrapper.orderBy(true, asc, DeviceProductionSummaryDO::getShiftEndTs);
        } else {
            wrapper.orderBy(true, false, DeviceProductionSummaryDO::getCreateTime);
        }

        Page<DeviceProductionSummaryDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceProductionSummaryDO> result = deviceProductionSummaryMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public DeviceProductionSummaryDO findByShift(Long deviceId, LocalDate shiftDate, Integer shiftCode) {
        LambdaQueryWrapper<DeviceProductionSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceProductionSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceProductionSummaryDO::getShiftDate, shiftDate)
                .eq(DeviceProductionSummaryDO::getShiftCode, shiftCode)
                .last("limit 1");
        return deviceProductionSummaryMapper.selectOne(wrapper);
    }

    @Override
    public void insert(DeviceProductionSummaryDO entity) {
        deviceProductionSummaryMapper.insert(entity);
    }

    @Override
    public void update(DeviceProductionSummaryDO entity) {
        deviceProductionSummaryMapper.updateById(entity);
    }
}


