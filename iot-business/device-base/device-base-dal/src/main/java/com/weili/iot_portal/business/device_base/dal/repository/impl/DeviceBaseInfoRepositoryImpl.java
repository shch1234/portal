package com.weili.iot_portal.business.device_base.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.business.device_base.dal.mapper.DeviceBaseInfoMapper;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceBaseInfoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 设备基础信息仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceBaseInfoRepositoryImpl implements DeviceBaseInfoRepository {

    private final DeviceBaseInfoMapper mapper;

    @Override
    public Optional<DeviceBaseInfoDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getId, id)));
    }

    @Override
    public Optional<DeviceBaseInfoDO> findByDeviceCode(String tenantId, String deviceCode) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getDeviceCode, deviceCode)));
    }

    @Override
    public Optional<DeviceBaseInfoDO> findByTbDeviceId(String tenantId, String tbDeviceId) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getTbDeviceId, tbDeviceId)));
    }

    @Override
    public boolean existsByDeviceCode(String tenantId, String deviceCode, String excludeId) {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getDeviceCode, deviceCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceBaseInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByTbDeviceId(String tenantId, String tbDeviceId, String excludeId) {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getTbDeviceId, tbDeviceId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceBaseInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<DeviceBaseInfoDO> findByFactoryId(String tenantId, String factoryId) {
        return mapper.selectList(tenantScope(tenantId)
                .eq(DeviceBaseInfoDO::getFactoryId, factoryId));
    }

    @Override
    public PageResult<DeviceBaseInfoDO> selectPage(DeviceBaseInfoPageQuery query) {
        Page<DeviceBaseInfoDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = tenantScope(query.getTenantId());
        if (StringUtils.isNotBlank(query.getDeviceCodeLike())) {
            wrapper.like(DeviceBaseInfoDO::getDeviceCode, query.getDeviceCodeLike());
        }
        if (StringUtils.isNotBlank(query.getDeviceNameLike())) {
            wrapper.like(DeviceBaseInfoDO::getDeviceName, query.getDeviceNameLike());
        }
        if (query.getDeviceTypeIds() != null && !query.getDeviceTypeIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceTypeId, query.getDeviceTypeIds());
        }
        if (query.getDeviceSubTypeNames() != null && !query.getDeviceSubTypeNames().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceSubTypeName, query.getDeviceSubTypeNames());
        }
        if (query.getDeviceModelIds() != null && !query.getDeviceModelIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceModelId, query.getDeviceModelIds());
        }
        if (query.getFactoryIds() != null && !query.getFactoryIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getFactoryId, query.getFactoryIds());
        }
        if (query.getWorkshopIds() != null && !query.getWorkshopIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getWorkshopId, query.getWorkshopIds());
        }
        if (query.getProductionLineIds() != null && !query.getProductionLineIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getProductionLineId, query.getProductionLineIds());
        }
        if (query.getDeviceStatuses() != null && !query.getDeviceStatuses().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceStatus, query.getDeviceStatuses());
        }
        if (query.getIsMonitored() != null) {
            wrapper.eq(DeviceBaseInfoDO::getIsMonitored, query.getIsMonitored());
        }
        // 注意：hasAlarm 字段在 Service 层处理，因为需要查询报警数据

        this.applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceBaseInfoDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceBaseInfoDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceBaseInfoDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(DeviceBaseInfoDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceBaseInfoDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceBaseInfoDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceBaseInfoDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceBaseInfoDO::getCreateTime);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        wrapper.orderBy(true, asc, switch (sortBy) {
            case "deviceCode" -> DeviceBaseInfoDO::getDeviceCode;
            case "deviceName" -> DeviceBaseInfoDO::getDeviceName;
            case "updatedTime" -> DeviceBaseInfoDO::getUpdateTime;
            default -> DeviceBaseInfoDO::getCreateTime;
        });
    }
}

