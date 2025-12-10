package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceModelDO;
import com.weili.iot_portal.dal.ddd.device.DeviceModelPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceModelRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceModelMapper;
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
    public Optional<DeviceModelDO> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceModelDO>().eq(DeviceModelDO::getId, id)));
    }

    @Override
    public Optional<DeviceModelDO> findByModelCode(String modelCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceModelDO>().eq(DeviceModelDO::getModelCode, modelCode)));
    }

    @Override
    public boolean existsByModelCode(String modelCode, String excludeId) {
        LambdaQueryWrapper<DeviceModelDO> wrapper = new LambdaQueryWrapper<DeviceModelDO>()
                .eq(DeviceModelDO::getModelCode, modelCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceModelDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceModelDO> selectPage(DeviceModelPageQuery query) {
        LambdaQueryWrapper<DeviceModelDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getModelCodeLike())) {
            wrapper.like(DeviceModelDO::getModelCode, query.getModelCodeLike());
        }
        if (StringUtils.isNotBlank(query.getModelNameLike())) {
            wrapper.like(DeviceModelDO::getModelName, query.getModelNameLike());
        }
        if (query.getDeviceTypeCodes() != null && !query.getDeviceTypeCodes().isEmpty()) {
            wrapper.in(DeviceModelDO::getDeviceTypeCode, query.getDeviceTypeCodes());
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
    public boolean deleteById(String id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceModelDO>().eq(DeviceModelDO::getId, id)) > 0;
    }

    private void applySort(LambdaQueryWrapper<DeviceModelDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceModelDO::getCreateTime);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        switch (sortBy) {
            case "modelCode" -> wrapper.orderBy(true, asc, DeviceModelDO::getModelCode);
            case "modelName" -> wrapper.orderBy(true, asc, DeviceModelDO::getModelName);
            case "manufacturer" -> wrapper.orderBy(true, asc, DeviceModelDO::getManufacturer);
            case "updatedTime" -> wrapper.orderBy(true, asc, DeviceModelDO::getUpdateTime);
            default -> wrapper.orderBy(true, asc, DeviceModelDO::getCreateTime);
        }
    }
}

