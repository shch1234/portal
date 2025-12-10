package com.weili.iot_portal.dal.repository.devicebase.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.OrganizationUnitDO;
import com.weili.iot_portal.dal.ddd.device.OrganizationUnitPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.OrganizationUnitRepository;
import com.weili.iot_portal.dal.mapper.devicebase.OrganizationUnitMapper;
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
    public Optional<OrganizationUnitDO> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<OrganizationUnitDO>().eq(OrganizationUnitDO::getId, id)));
    }

    @Override
    public Optional<OrganizationUnitDO> findByUnitCode(String unitCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<OrganizationUnitDO>().eq(OrganizationUnitDO::getUnitCode, unitCode)));
    }

    @Override
    public List<OrganizationUnitDO> findByParentId(String parentId) {
        return mapper.selectList(new LambdaQueryWrapper<OrganizationUnitDO>().eq(OrganizationUnitDO::getOrgParentId, parentId));
    }

    @Override
    public boolean existsByUnitCode(String unitCode, String excludeId) {
        LambdaQueryWrapper<OrganizationUnitDO> wrapper = new LambdaQueryWrapper<OrganizationUnitDO>()
                .eq(OrganizationUnitDO::getUnitCode, unitCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(OrganizationUnitDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<OrganizationUnitDO> selectPage(OrganizationUnitPageQuery query) {
        LambdaQueryWrapper<OrganizationUnitDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUnitCodeLike())) {
            wrapper.like(OrganizationUnitDO::getUnitCode, query.getUnitCodeLike());
        }
        if (StringUtils.isNotBlank(query.getUnitNameLike())) {
            wrapper.like(OrganizationUnitDO::getUnitName, query.getUnitNameLike());
        }
        if (query.getUnitTypeValues() != null && !query.getUnitTypeValues().isEmpty()) {
            wrapper.in(OrganizationUnitDO::getUnitTypeValue, query.getUnitTypeValues());
        }
        if (StringUtils.isNotBlank(query.getOrgParentId())) {
            wrapper.eq(OrganizationUnitDO::getOrgParentId, query.getOrgParentId());
        }
        if (query.getLevelNo() != null) {
            wrapper.eq(OrganizationUnitDO::getLevelNo, query.getLevelNo());
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
    public boolean deleteById(String id) {
        return mapper.delete(new LambdaQueryWrapper<OrganizationUnitDO>().eq(OrganizationUnitDO::getId, id)) > 0;
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
            case "levelNo" -> OrganizationUnitDO::getLevelNo;
            case "createdTime" -> OrganizationUnitDO::getCreateTime;
            case "updatedTime" -> OrganizationUnitDO::getUpdateTime;
            default -> OrganizationUnitDO::getSortOrder;
        });
    }
}

