package com.weili.iot_portal.business.device_base.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceModelDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceModelPageQuery;
import com.weili.iot_portal.business.device_base.dal.mapper.DeviceModelMapper;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceModelRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 设备型号仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceModelRepositoryImpl implements DeviceModelRepository {

    private final DeviceModelMapper mapper;

    @Override
    public Optional<DeviceModelDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceModelDO::getId, id)));
    }

    @Override
    public Optional<DeviceModelDO> findByModelCode(String tenantId, String modelCode) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceModelDO::getModelCode, modelCode)));
    }

    @Override
    public boolean existsByModelCode(String tenantId, String modelCode, String excludeId) {
        LambdaQueryWrapper<DeviceModelDO> wrapper = tenantScope(tenantId)
                .eq(DeviceModelDO::getModelCode, modelCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceModelDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceModelDO> selectPage(DeviceModelPageQuery query) {
        LambdaQueryWrapper<DeviceModelDO> wrapper = tenantScope(query.getTenantId());
        if (StringUtils.isNotBlank(query.getModelCodeLike())) {
            wrapper.like(DeviceModelDO::getModelCode, query.getModelCodeLike());
        }
        if (StringUtils.isNotBlank(query.getModelNameLike())) {
            wrapper.like(DeviceModelDO::getModelName, query.getModelNameLike());
        }
        if (query.getDeviceTypeIds() != null && !query.getDeviceTypeIds().isEmpty()) {
            wrapper.in(DeviceModelDO::getDeviceTypeId, query.getDeviceTypeIds());
        }
        if (StringUtils.isNotBlank(query.getManufacturer())) {
            wrapper.like(DeviceModelDO::getManufacturer, query.getManufacturer());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceModelDO::getIsActive, query.getIsActive());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceModelDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceModelDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceModelDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceModelDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(DeviceModelDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceModelDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<DeviceModelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceModelDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceModelDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceModelDO::getCreateTime);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        wrapper.orderBy(true, asc, switch (sortBy) {
            case "modelCode" -> DeviceModelDO::getModelCode;
            case "modelName" -> DeviceModelDO::getModelName;
            case "manufacturer" -> DeviceModelDO::getManufacturer;
            case "updatedTime" -> DeviceModelDO::getUpdateTime;
            default -> DeviceModelDO::getCreateTime;
        });
    }
}

