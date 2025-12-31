package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceTypeRelationDO;
import com.weili.iot_portal.dal.ddd.device.DeviceTypePageQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceTypeRelationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceTypeRelationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 设备类型仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceTypeRelationRepositoryImpl implements DeviceTypeRelationRepository {

    private final DeviceTypeRelationMapper mapper;


    @Override
    public Optional<DeviceTypeRelationDO> findById(Long id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceTypeRelationDO>()
                .eq(DeviceTypeRelationDO::getId, id)));
    }

    @Override
    public Optional<DeviceTypeRelationDO> findByTypeCode(String typeCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceTypeRelationDO>().eq(DeviceTypeRelationDO::getTypeCode, typeCode)));
    }

    @Override
    public List<DeviceTypeRelationDO> findByParentTypeId(Long parentTypeId) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceTypeRelationDO>().eq(DeviceTypeRelationDO::getParentTypeId, parentTypeId));
    }

    @Override
    public List<DeviceTypeRelationDO> findByParentTypeCode(String parentTypeCode) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceTypeRelationDO>().eq(DeviceTypeRelationDO::getParentTypeCode, parentTypeCode));
    }

    @Override
    public boolean existsByTypeCode(String typeCode, Long excludeId) {
        LambdaQueryWrapper<DeviceTypeRelationDO> wrapper = new LambdaQueryWrapper<DeviceTypeRelationDO>()
                .eq(DeviceTypeRelationDO::getTypeCode, typeCode);
        if (excludeId != null) {
            wrapper.ne(DeviceTypeRelationDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceTypeRelationDO> selectPage(DeviceTypePageQuery query) {
        LambdaQueryWrapper<DeviceTypeRelationDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getTypeCode())) {
            wrapper.like(DeviceTypeRelationDO::getTypeCode, query.getTypeCode());
        }
        if (StringUtils.isNotBlank(query.getParentTypeId())) {
            wrapper.eq(DeviceTypeRelationDO::getParentTypeId, query.getParentTypeId());
        }
        if (query.getLevelNo() != null) {
            wrapper.eq(DeviceTypeRelationDO::getLevelNo, query.getLevelNo());
        }
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            wrapper.eq(DeviceTypeRelationDO::getCategory, query.getCategory());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(DeviceTypeRelationDO::getIsActive, query.getIsActive());
        }
        if (query.getDescription() != null && !query.getDescription().isEmpty()) {
            wrapper.like(DeviceTypeRelationDO::getDescription, query.getDescription());
        }
        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceTypeRelationDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceTypeRelationDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceTypeRelationDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceTypeRelationDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(Long id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceTypeRelationDO>().eq(DeviceTypeRelationDO::getId, id)) > 0;
    }

    @Override
    public List<DeviceTypeRelationDO> selectByCodes(List<String> deviceTypeCodes) {
        if (CollectionUtils.isEmpty(deviceTypeCodes)) {
            return Collections.emptyList();
        }
        return mapper.selectList(new LambdaQueryWrapper<DeviceTypeRelationDO>().in(DeviceTypeRelationDO::getTypeCode, deviceTypeCodes));
    }

    private void applySort(LambdaQueryWrapper<DeviceTypeRelationDO> wrapper, String sortBy, String sortDirection) {
        // 默认排序：先按sortOrder升序，再按typeCode升序
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByAsc(DeviceTypeRelationDO::getSortOrder).orderByAsc(DeviceTypeRelationDO::getTypeCode);
            return;
        }

        boolean isAscending = !"desc".equalsIgnoreCase(sortDirection);

        // 根据排序字段选择对应的属性
        SFunction<DeviceTypeRelationDO, ?> orderByColumn = getOrderByColumn(sortBy);
        wrapper.orderBy(true, isAscending, orderByColumn);
    }

    private SFunction<DeviceTypeRelationDO, ?> getOrderByColumn(String sortBy) {
        return switch (sortBy) {
            case "typeCode" -> DeviceTypeRelationDO::getTypeCode;
            case "levelNo" -> DeviceTypeRelationDO::getLevelNo;
            case "createdTime" -> DeviceTypeRelationDO::getCreateTime;
            case "updatedTime" -> DeviceTypeRelationDO::getUpdateTime;
            default -> DeviceTypeRelationDO::getSortOrder;
        };
    }
}

