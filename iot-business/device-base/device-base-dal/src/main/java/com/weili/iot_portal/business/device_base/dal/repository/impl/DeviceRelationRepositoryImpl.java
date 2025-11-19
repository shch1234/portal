package com.weili.iot_portal.business.device_base.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceRelationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceRelationPageQuery;
import com.weili.iot_portal.business.device_base.dal.mapper.DeviceRelationMapper;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceRelationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 设备关系仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceRelationRepositoryImpl implements DeviceRelationRepository {

    private final DeviceRelationMapper deviceRelationMapper;

    @Override
    public Optional<DeviceRelationDO> findById(String tenantId, String id) {
        return Optional.ofNullable(deviceRelationMapper.selectOne(baseQuery(tenantId).eq(DeviceRelationDO::getId, id)));
    }

    @Override
    public PageResult<DeviceRelationDO> selectPage(DeviceRelationPageQuery query) {
        LambdaQueryWrapper<DeviceRelationDO> wrapper = baseQuery(query.getTenantId());
        if (StringUtils.isNotBlank(query.getFromDeviceId())) {
            wrapper.eq(DeviceRelationDO::getFromDeviceId, query.getFromDeviceId());
        }
        if (StringUtils.isNotBlank(query.getToDeviceId())) {
            wrapper.eq(DeviceRelationDO::getToDeviceId, query.getToDeviceId());
        }
        if (query.getRelationTypes() != null && !query.getRelationTypes().isEmpty()) {
            wrapper.in(DeviceRelationDO::getRelationType, query.getRelationTypes());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceRelationDO::getIsActive, query.getIsActive());
        }
        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceRelationDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceRelationDO> result = deviceRelationMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public boolean existsRelation(String tenantId, String fromDeviceId, String toDeviceId, String relationType, String excludeId) {
        LambdaQueryWrapper<DeviceRelationDO> wrapper = baseQuery(tenantId)
                .eq(DeviceRelationDO::getFromDeviceId, fromDeviceId)
                .eq(DeviceRelationDO::getToDeviceId, toDeviceId)
                .eq(DeviceRelationDO::getRelationType, relationType)
                .eq(DeviceRelationDO::getIsActive, Boolean.TRUE);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceRelationDO::getId, excludeId);
        }
        return deviceRelationMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(DeviceRelationDO entity) {
        deviceRelationMapper.insert(entity);
    }

    @Override
    public void update(DeviceRelationDO entity) {
        deviceRelationMapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return deviceRelationMapper.delete(baseQuery(tenantId).eq(DeviceRelationDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceRelationDO> baseQuery(String tenantId) {
        LambdaQueryWrapper<DeviceRelationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceRelationDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceRelationDO> wrapper, String sortBy, String sortDirection) {
        boolean asc = "asc".equalsIgnoreCase(sortDirection);
        if (StringUtils.isBlank(sortBy) || "createdTime".equals(sortBy)) {
            wrapper.orderBy(true, asc, DeviceRelationDO::getCreateTime);
            return;
        }
        switch (sortBy) {
            case "updatedTime" -> wrapper.orderBy(true, asc, DeviceRelationDO::getUpdateTime);
            case "relationType" -> wrapper.orderBy(true, asc, DeviceRelationDO::getRelationType);
            default -> wrapper.orderBy(true, asc, DeviceRelationDO::getCreateTime);
        }
    }
}

