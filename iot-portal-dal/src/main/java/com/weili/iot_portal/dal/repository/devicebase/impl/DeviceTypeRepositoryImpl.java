package com.weili.iot_portal.dal.repository.devicebase.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceTypeDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceTypeRepository;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceTypeMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备类型仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceTypeRepositoryImpl implements DeviceTypeRepository {

    private final DeviceTypeMapper mapper;

    @Override
    public Optional<DeviceTypeDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceTypeDO::getId, id)));
    }

    @Override
    public Optional<DeviceTypeDO> findByTypeCode(String tenantId, String typeCode) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceTypeDO::getTypeCode, typeCode)));
    }

    @Override
    public List<DeviceTypeDO> findByParentTypeId(String tenantId, String parentTypeId) {
        return mapper.selectList(tenantScope(tenantId).eq(DeviceTypeDO::getParentTypeId, parentTypeId));
    }

    @Override
    public boolean existsByTypeCode(String tenantId, String typeCode, String excludeId) {
        LambdaQueryWrapper<DeviceTypeDO> wrapper = tenantScope(tenantId)
                .eq(DeviceTypeDO::getTypeCode, typeCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceTypeDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceTypeDO> selectPage(DeviceTypePageQuery query) {
        LambdaQueryWrapper<DeviceTypeDO> wrapper = tenantScope(query.getTenantUuid());
        if (StringUtils.isNotBlank(query.getTypeCodeLike())) {
            wrapper.like(DeviceTypeDO::getTypeCode, query.getTypeCodeLike());
        }
        if (StringUtils.isNotBlank(query.getTypeDictValueLike())) {
            wrapper.like(DeviceTypeDO::getTypeDictValue, query.getTypeDictValueLike());
        }
        if (StringUtils.isNotBlank(query.getParentTypeId())) {
            wrapper.eq(DeviceTypeDO::getParentTypeId, query.getParentTypeId());
        }
        if (query.getLevelNo() != null) {
            wrapper.eq(DeviceTypeDO::getLevelNo, query.getLevelNo());
        }
        if (query.getCategories() != null && !query.getCategories().isEmpty()) {
            wrapper.in(DeviceTypeDO::getCategory, query.getCategories());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceTypeDO::getIsActive, query.getIsActive());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceTypeDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceTypeDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceTypeDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceTypeDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(DeviceTypeDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceTypeDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<DeviceTypeDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceTypeDO::getTenantUuid, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceTypeDO> wrapper, String sortBy, String sortDirection) {
        // 默认排序：先按sortOrder升序，再按typeCode升序
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByAsc(DeviceTypeDO::getSortOrder).orderByAsc(DeviceTypeDO::getTypeCode);
            return;
        }

        boolean isAscending = !"desc".equalsIgnoreCase(sortDirection);

        // 根据排序字段选择对应的属性
        SFunction<DeviceTypeDO, ?> orderByColumn = getOrderByColumn(sortBy);
        wrapper.orderBy(true, isAscending, orderByColumn);
    }

    private SFunction<DeviceTypeDO, ?> getOrderByColumn(String sortBy) {
        return switch (sortBy) {
            case "typeCode" -> DeviceTypeDO::getTypeCode;
            case "typeDictValue" -> DeviceTypeDO::getTypeDictValue;
            case "levelNo" -> DeviceTypeDO::getLevelNo;
            case "createdTime" -> DeviceTypeDO::getCreateTime;
            case "updatedTime" -> DeviceTypeDO::getUpdateTime;
            default -> DeviceTypeDO::getSortOrder;
        };
    }
}

