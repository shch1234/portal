package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.mapper.device.DeviceInfoMapper;
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
public class DeviceInfoRepositoryImpl implements DeviceInfoRepository {

    private final DeviceInfoMapper mapper;

    @Override
    public Optional<DeviceInfoDO> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getId, id)));
    }

    @Override
    public Optional<DeviceInfoDO> findByDeviceCode(String deviceCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeviceCode, deviceCode)));
    }

    @Override
    public Optional<DeviceInfoDO> findByTbDeviceId(String tbDeviceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getTbDeviceId, tbDeviceId)));
    }

    @Override
    public boolean existsByDeviceCode(String deviceCode, String excludeId) {
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeviceCode, deviceCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByTbDeviceId(String tbDeviceId, String excludeId) {
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getTbDeviceId, tbDeviceId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<DeviceInfoDO> findByFactoryId(String factoryId) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getOrgFactoryId, factoryId));
    }

    @Override
    public Optional<DeviceInfoDO> findActiveMonitoredByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeviceCode, deviceCode)
                .eq(DeviceInfoDO::getDeleted, 0)
                .eq(DeviceInfoDO::getIsMonitored, Boolean.TRUE)
                .eq(DeviceInfoDO::getDeviceStatus, "ACTIVE");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public List<DeviceInfoDO> findAllActive() {
        return mapper.selectList(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeleted, false));
    }

    @Override
    public List<DeviceInfoDO> findActiveWithFactory() {
        return mapper.selectList(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeleted, false)
                .isNotNull(DeviceInfoDO::getOrgFactoryId));
    }

    @Override
    public PageResult<DeviceInfoDO> selectPage(DeviceBaseInfoPageQuery query) {
        Page<DeviceInfoDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getDeviceCodeLike())) {
            wrapper.like(DeviceInfoDO::getDeviceCode, query.getDeviceCodeLike());
        }
        if (StringUtils.isNotBlank(query.getDeviceNameLike())) {
            wrapper.like(DeviceInfoDO::getDeviceName, query.getDeviceNameLike());
        }
        if (query.getDeviceTypeCodes() != null && !query.getDeviceTypeCodes().isEmpty()) {
            wrapper.in(DeviceInfoDO::getDeviceTypeCode, query.getDeviceTypeCodes());
        }
        if (query.getDeviceSubTypeNames() != null && !query.getDeviceSubTypeNames().isEmpty()) {
            wrapper.in(DeviceInfoDO::getDeviceSubTypeName, query.getDeviceSubTypeNames());
        }
        if (query.getDeviceModelIds() != null && !query.getDeviceModelIds().isEmpty()) {
            wrapper.in(DeviceInfoDO::getDeviceModelId, query.getDeviceModelIds());
        }
        if (query.getOrgFactoryIds() != null && !query.getOrgFactoryIds().isEmpty()) {
            wrapper.in(DeviceInfoDO::getOrgFactoryId, query.getOrgFactoryIds());
        }
        if (query.getOrgWorkshopIds() != null && !query.getOrgWorkshopIds().isEmpty()) {
            wrapper.in(DeviceInfoDO::getOrgWorkshopId, query.getOrgWorkshopIds());
        }
        if (query.getOrgProductionLineIds() != null && !query.getOrgProductionLineIds().isEmpty()) {
            wrapper.in(DeviceInfoDO::getOrgProductionLineId, query.getOrgProductionLineIds());
        }
        if (query.getDeviceStatuses() != null && !query.getDeviceStatuses().isEmpty()) {
            wrapper.in(DeviceInfoDO::getDeviceStatus, query.getDeviceStatuses());
        }
        if (query.getIsMonitored() != null) {
            wrapper.eq(DeviceInfoDO::getIsMonitored, query.getIsMonitored());
        }
        this.applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceInfoDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceInfoDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceInfoDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceInfoDO>().eq(DeviceInfoDO::getId, id)) > 0;
    }

    private void applySort(LambdaQueryWrapper<DeviceInfoDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderBy(true, false, DeviceInfoDO::getCreateTime);
            return;
        }

        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        // 根据排序字段应用相应的排序规则
        switch (sortBy) {
            case "deviceCode" -> wrapper.orderBy(true, asc, DeviceInfoDO::getDeviceCode);
            case "deviceName" -> wrapper.orderBy(true, asc, DeviceInfoDO::getDeviceName);
            case "updatedTime" -> wrapper.orderBy(true, asc, DeviceInfoDO::getUpdateTime);
            default -> wrapper.orderBy(true, false, DeviceInfoDO::getCreateTime);
        }
    }
}

