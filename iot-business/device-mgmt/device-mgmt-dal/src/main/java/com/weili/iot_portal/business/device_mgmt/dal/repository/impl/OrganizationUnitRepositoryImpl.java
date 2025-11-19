package com.weili.iot_portal.business.device_mgmt.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_mgmt.dal.dataobject.OrganizationUnitDO;
import com.weili.iot_portal.business.device_mgmt.dal.ddd.OrganizationUnitPageQuery;
import com.weili.iot_portal.business.device_mgmt.dal.mapper.OrganizationUnitMapper;
import com.weili.iot_portal.business.device_mgmt.dal.repository.OrganizationUnitRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元仓储实现
 */
@Repository
@RequiredArgsConstructor
public class OrganizationUnitRepositoryImpl implements OrganizationUnitRepository {

    private final OrganizationUnitMapper mapper;

    @Override
    public Optional<OrganizationUnitDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(OrganizationUnitDO::getId, id)));
    }

    @Override
    public Optional<OrganizationUnitDO> findByUnitCode(String tenantId, String unitCode) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(OrganizationUnitDO::getUnitCode, unitCode)));
    }

    @Override
    public List<OrganizationUnitDO> findByParentId(String tenantId, String parentId) {
        return mapper.selectList(tenantScope(tenantId).eq(OrganizationUnitDO::getParentId, parentId));
    }

    @Override
    public boolean existsByUnitCode(String tenantId, String unitCode, String excludeId) {
        LambdaQueryWrapper<OrganizationUnitDO> wrapper = tenantScope(tenantId)
                .eq(OrganizationUnitDO::getUnitCode, unitCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(OrganizationUnitDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<OrganizationUnitDO> selectPage(OrganizationUnitPageQuery query) {
        LambdaQueryWrapper<OrganizationUnitDO> wrapper = tenantScope(query.getTenantId());
        if (StringUtils.isNotBlank(query.getUnitCodeLike())) {
            wrapper.like(OrganizationUnitDO::getUnitCode, query.getUnitCodeLike());
        }
        if (StringUtils.isNotBlank(query.getUnitNameLike())) {
            wrapper.like(OrganizationUnitDO::getUnitName, query.getUnitNameLike());
        }
        if (query.getUnitTypes() != null && !query.getUnitTypes().isEmpty()) {
            wrapper.in(OrganizationUnitDO::getUnitType, query.getUnitTypes());
        }
        if (StringUtils.isNotBlank(query.getParentId())) {
            wrapper.eq(OrganizationUnitDO::getParentId, query.getParentId());
        }
        if (query.getLevel() != null) {
            wrapper.eq(OrganizationUnitDO::getLevel, query.getLevel());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(OrganizationUnitDO::getIsActive, query.getIsActive());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<OrganizationUnitDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<OrganizationUnitDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(OrganizationUnitDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(OrganizationUnitDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(OrganizationUnitDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<OrganizationUnitDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<OrganizationUnitDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrganizationUnitDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<OrganizationUnitDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByAsc(OrganizationUnitDO::getSortOrder).orderByDesc(OrganizationUnitDO::getCreateTime);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        wrapper.orderBy(true, asc, switch (sortBy) {
            case "unitCode" -> OrganizationUnitDO::getUnitCode;
            case "unitName" -> OrganizationUnitDO::getUnitName;
            case "level" -> OrganizationUnitDO::getLevel;
            case "createdTime" -> OrganizationUnitDO::getCreateTime;
            case "updatedTime" -> OrganizationUnitDO::getUpdateTime;
            default -> OrganizationUnitDO::getSortOrder;
        });
    }
}


