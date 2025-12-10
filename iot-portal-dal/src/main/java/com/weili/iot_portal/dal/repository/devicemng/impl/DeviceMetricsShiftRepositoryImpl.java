package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceMetricsShiftDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceMetricsShiftRepository;
import com.weili.iot_portal.dal.mapper.devicemng.DeviceMetricsShiftMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 设备班次指标仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceMetricsShiftRepositoryImpl implements DeviceMetricsShiftRepository {

    private final DeviceMetricsShiftMapper mapper;

    @Override
    public Optional<DeviceMetricsShiftDO> selectLatestFinalized(String deviceId) {
        LambdaQueryWrapper<DeviceMetricsShiftDO> wrapper = baseQuery(deviceId)
                .eq(DeviceMetricsShiftDO::getIsFinalized, Boolean.TRUE)
                .orderByDesc(DeviceMetricsShiftDO::getShiftStartTs)
                .last("limit 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public PageResult<DeviceMetricsShiftDO> selectPage(String deviceId,
                                                       Long startTs, Long endTs, int pageNo, int pageSize) {
        LambdaQueryWrapper<DeviceMetricsShiftDO> wrapper = baseQuery(deviceId);
        if (startTs != null) {
            wrapper.ge(DeviceMetricsShiftDO::getShiftStartTs, startTs);
        }
        if (endTs != null) {
            wrapper.le(DeviceMetricsShiftDO::getShiftEndTs, endTs);
        }
        wrapper.orderByDesc(DeviceMetricsShiftDO::getShiftStartTs);

        Page<DeviceMetricsShiftDO> page = new Page<>(pageNo, pageSize);
        Page<DeviceMetricsShiftDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    /**
     * 构建基础查询条件（对应 device_metrics_summary 表的字段）
     */
    private LambdaQueryWrapper<DeviceMetricsShiftDO> baseQuery(String deviceId) {
        LambdaQueryWrapper<DeviceMetricsShiftDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(deviceId)) {
            wrapper.eq(DeviceMetricsShiftDO::getDeviceInfoId, deviceId);
        }
        return wrapper;
    }
}


