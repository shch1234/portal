package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicemng.ProductionCounterDO;
import com.weili.iot_portal.dal.ddd.device.ProductionCounterPageQuery;
import com.weili.iot_portal.dal.repository.devicemng.ProductionCounterRepository;
import com.weili.iot_portal.dal.mapper.devicemng.ProductionCounterMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 班次产量仓储实现
 */
@Repository
@RequiredArgsConstructor
public class ProductionCounterRepositoryImpl implements ProductionCounterRepository {

    private final ProductionCounterMapper productionCounterMapper;

    /**
     * 查找当前班次的产量统计（对应 device_production_summary 表的字段）
     */
    @Override
    public Optional<ProductionCounterDO> findCurrent(String tenantId, String deviceId, long currentTs) {
        LambdaQueryWrapper<ProductionCounterDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionCounterDO::getTenantUuid, tenantId)
                .eq(ProductionCounterDO::getDeviceInfoId, deviceId)
                .le(ProductionCounterDO::getShiftStartTs, currentTs)
                .ge(ProductionCounterDO::getShiftEndTs, currentTs)
                .orderByDesc(ProductionCounterDO::getShiftStartTs)
                .last("limit 1");
        ProductionCounterDO current = productionCounterMapper.selectOne(wrapper);
        if (current != null) {
            return Optional.of(current);
        }
        // 如果当前没有进行中的班次，返回最近一班
        LambdaQueryWrapper<ProductionCounterDO> latestWrapper = new LambdaQueryWrapper<>();
        latestWrapper.eq(ProductionCounterDO::getTenantUuid, tenantId)
                .eq(ProductionCounterDO::getDeviceInfoId, deviceId)
                .orderByDesc(ProductionCounterDO::getShiftStartTs)
                .last("limit 1");
        return Optional.ofNullable(productionCounterMapper.selectOne(latestWrapper));
    }

    /**
     * 分页查询产量统计（对应 device_production_summary 表的字段）
     */
    @Override
    public PageResult<ProductionCounterDO> selectPage(ProductionCounterPageQuery query) {
        LambdaQueryWrapper<ProductionCounterDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductionCounterDO::getTenantUuid, query.getTenantId());
        if (StringUtils.isNotBlank(query.getDeviceId())) {
            wrapper.eq(ProductionCounterDO::getDeviceInfoId, query.getDeviceId());
        }
        if (query.getStartTs() != null) {
            wrapper.ge(ProductionCounterDO::getShiftStartTs, query.getStartTs());
        }
        if (query.getEndTs() != null) {
            wrapper.le(ProductionCounterDO::getShiftEndTs, query.getEndTs());
        }
        boolean asc = "asc".equalsIgnoreCase(query.getSortDirection());
        if (StringUtils.isBlank(query.getSortBy()) || "shiftStartTs".equalsIgnoreCase(query.getSortBy())) {
            wrapper.orderBy(true, asc, ProductionCounterDO::getShiftStartTs);
        } else if ("shiftEndTs".equalsIgnoreCase(query.getSortBy())) {
            wrapper.orderBy(true, asc, ProductionCounterDO::getShiftEndTs);
        } else {
            wrapper.orderBy(true, false, ProductionCounterDO::getCreateTime);
        }

        Page<ProductionCounterDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<ProductionCounterDO> result = productionCounterMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }
}


