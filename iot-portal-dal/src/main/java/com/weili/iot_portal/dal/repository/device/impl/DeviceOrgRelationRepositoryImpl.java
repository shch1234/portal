package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceOrgRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceOrgRelationPageQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceOrgRelationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceOrgRelationRepository;
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
public class DeviceOrgRelationRepositoryImpl implements DeviceOrgRelationRepository {

    private final DeviceOrgRelationMapper mapper;

    @Override
    public Optional<DeviceOrgRelationDO> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceOrgRelationDO>().eq(DeviceOrgRelationDO::getId, id)));
    }

    @Override
    public Optional<DeviceOrgRelationDO> findByUnitCode(String unitCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceOrgRelationDO>().eq(DeviceOrgRelationDO::getUnitCode, unitCode)));
    }

    @Override
    public List<DeviceOrgRelationDO> findByParentId(String parentId) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceOrgRelationDO>().eq(DeviceOrgRelationDO::getOrgParentId, parentId));
    }

    @Override
    public boolean existsByUnitCode(String unitCode, String excludeId) {
        LambdaQueryWrapper<DeviceOrgRelationDO> wrapper = new LambdaQueryWrapper<DeviceOrgRelationDO>()
                .eq(DeviceOrgRelationDO::getUnitCode, unitCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceOrgRelationDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceOrgRelationDO> selectPage(DeviceOrgRelationPageQuery query) {
        LambdaQueryWrapper<DeviceOrgRelationDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUnitCode())) {
            wrapper.like(DeviceOrgRelationDO::getUnitCode, query.getUnitCode());
        }
        if (StringUtils.isNotBlank(query.getUnitName())) {
            wrapper.like(DeviceOrgRelationDO::getUnitName, query.getUnitName());
        }
        if (StringUtils.isNotBlank(query.getUnitTypeValue())) {
            wrapper.in(DeviceOrgRelationDO::getUnitTypeValue, query.getUnitTypeValue());
        }
        if (StringUtils.isNotBlank(query.getOrgParentId())) {
            wrapper.eq(DeviceOrgRelationDO::getOrgParentId, query.getOrgParentId());
        }
        if (query.getLevelNo() != null) {
            wrapper.eq(DeviceOrgRelationDO::getLevelNo, query.getLevelNo());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceOrgRelationDO::getIsActive, query.getIsActive());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceOrgRelationDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceOrgRelationDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceOrgRelationDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceOrgRelationDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceOrgRelationDO>().eq(DeviceOrgRelationDO::getId, id)) > 0;
    }

    @Override
    public List<DeviceOrgRelationDO> listByIds(List<Long> ids) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceOrgRelationDO>().in(DeviceOrgRelationDO::getId, ids));
    }

    @Override
    public List<DeviceOrgRelationDO> findAllActive() {
        return mapper.selectList(new LambdaQueryWrapper<DeviceOrgRelationDO>()
                .eq(DeviceOrgRelationDO::getIsActive, true)
                .orderByAsc(DeviceOrgRelationDO::getLevelNo)
                .orderByAsc(DeviceOrgRelationDO::getCreateTime));
    }

    private void applySort(LambdaQueryWrapper<DeviceOrgRelationDO> wrapper, String sortBy, String sortDirection) {
        // 如果 sortBy 为空，则使用默认排序：先按 sortOrder 升序，再按 createTime 降序
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceOrgRelationDO::getCreateTime);
            return;
        }

        // 判断是否升序（默认升序，除非显式指定为 desc）
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);

        // 根据不同的排序字段应用排序
        switch (sortBy) {
            case "unitCode" -> {
                if (asc) {
                    wrapper.orderByAsc(DeviceOrgRelationDO::getUnitCode);
                } else {
                    wrapper.orderByDesc(DeviceOrgRelationDO::getUnitCode);
                }
            }
            case "unitName" -> {
                if (asc) {
                    wrapper.orderByAsc(DeviceOrgRelationDO::getUnitName);
                } else {
                    wrapper.orderByDesc(DeviceOrgRelationDO::getUnitName);
                }
            }
            case "levelNo" -> {
                if (asc) {
                    wrapper.orderByAsc(DeviceOrgRelationDO::getLevelNo);
                } else {
                    wrapper.orderByDesc(DeviceOrgRelationDO::getLevelNo);
                }
            }
            case "createdTime" -> {
                if (asc) {
                    wrapper.orderByAsc(DeviceOrgRelationDO::getCreateTime);
                } else {
                    wrapper.orderByDesc(DeviceOrgRelationDO::getCreateTime);
                }
            }
            case "updatedTime" -> {
                if (asc) {
                    wrapper.orderByAsc(DeviceOrgRelationDO::getUpdateTime);
                } else {
                    wrapper.orderByDesc(DeviceOrgRelationDO::getUpdateTime);
                }
            }
        }
    }
}

