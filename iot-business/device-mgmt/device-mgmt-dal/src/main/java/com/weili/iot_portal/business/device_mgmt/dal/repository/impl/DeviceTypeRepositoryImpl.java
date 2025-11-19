package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.DeviceTypeDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.DeviceTypePageQuery;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.DeviceTypeMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.DeviceTypeRepository;
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
        LambdaQueryWrapper<DeviceTypeDO> wrapper = tenantScope(query.getTenantId());
        if (StringUtils.isNotBlank(query.getTypeCodeLike())) {
            wrapper.like(DeviceTypeDO::getTypeCode, query.getTypeCodeLike());
        }
        if (StringUtils.isNotBlank(query.getTypeNameLike())) {
            wrapper.like(DeviceTypeDO::getTypeName, query.getTypeNameLike());
        }
        if (StringUtils.isNotBlank(query.getParentTypeId())) {
            wrapper.eq(DeviceTypeDO::getParentTypeId, query.getParentTypeId());
        }
        if (query.getLevel() != null) {
            wrapper.eq(DeviceTypeDO::getLevel, query.getLevel());
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
        wrapper.eq(DeviceTypeDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceTypeDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByAsc(DeviceTypeDO::getSortOrder).orderByAsc(DeviceTypeDO::getTypeCode);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        wrapper.orderBy(true, asc, switch (sortBy) {
            case "typeCode" -> DeviceTypeDO::getTypeCode;
            case "typeName" -> DeviceTypeDO::getTypeName;
            case "level" -> DeviceTypeDO::getLevel;
            case "createdTime" -> DeviceTypeDO::getCreateTime;
            case "updatedTime" -> DeviceTypeDO::getUpdateTime;
            default -> DeviceTypeDO::getSortOrder;
        });
    }
}


