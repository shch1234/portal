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
import java.util.List;
import java.util.Map;
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
    public List<DeviceMetricSummaryDO> selectFinalizedInRange(Long deviceId, Long startTsMillis, Long endTsMillis) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceMetricSummaryDO::getDeviceInfoId, deviceId)
                .eq(DeviceMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (startTsMillis != null) {
            wrapper.ge(DeviceMetricSummaryDO::getShiftEndTs, startTsMillis);
        }
        if (endTsMillis != null) {
            wrapper.le(DeviceMetricSummaryDO::getShiftEndTs, endTsMillis);
        }
        wrapper.orderByAsc(DeviceMetricSummaryDO::getShiftEndTs);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<Long> findDistinctDeviceIdsWithFinalizedSummaries(Long startTsMillis, Long endTsMillis) {
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(DeviceMetricSummaryDO::getDeviceInfoId)
                .eq(DeviceMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (startTsMillis != null) {
            wrapper.ge(DeviceMetricSummaryDO::getShiftEndTs, startTsMillis);
        }
        if (endTsMillis != null) {
            wrapper.le(DeviceMetricSummaryDO::getShiftEndTs, endTsMillis);
        }
        return mapper.selectList(wrapper).stream()
                .map(DeviceMetricSummaryDO::getDeviceInfoId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Map<Long, List<DeviceMetricSummaryDO>> selectFinalizedInRangeBatch(
            List<Long> deviceIds, Long startTsMillis, Long endTsMillis) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        LambdaQueryWrapper<DeviceMetricSummaryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DeviceMetricSummaryDO::getDeviceInfoId, deviceIds)
                .eq(DeviceMetricSummaryDO::getIsFinalized, Boolean.TRUE);
        if (startTsMillis != null) {
            wrapper.ge(DeviceMetricSummaryDO::getShiftEndTs, startTsMillis);
        }
        if (endTsMillis != null) {
            wrapper.le(DeviceMetricSummaryDO::getShiftEndTs, endTsMillis);
        }
        wrapper.orderByAsc(DeviceMetricSummaryDO::getDeviceInfoId)
                .orderByAsc(DeviceMetricSummaryDO::getShiftEndTs);
        
        List<DeviceMetricSummaryDO> allRecords = mapper.selectList(wrapper);
        
        // 按设备ID分组
        return allRecords.stream()
                .collect(java.util.stream.Collectors.groupingBy(DeviceMetricSummaryDO::getDeviceInfoId));
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


