package com.weili.iot_portal.dal.repository.devicebase.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceBaseInfoMapper;
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
    public Optional<DeviceBaseInfoDO> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getId, id)));
    }

    @Override
    public Optional<DeviceBaseInfoDO> findByDeviceCode(String deviceCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getDeviceCode, deviceCode)));
    }

    @Override
    public Optional<DeviceBaseInfoDO> findByTbDeviceId(String tbDeviceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getTbDeviceId, tbDeviceId)));
    }

    @Override
    public boolean existsByDeviceCode(String deviceCode, String excludeId) {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getDeviceCode, deviceCode);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceBaseInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByTbDeviceId(String tbDeviceId, String excludeId) {
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getTbDeviceId, tbDeviceId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceBaseInfoDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<DeviceBaseInfoDO> findByFactoryId(String factoryId) {
        return mapper.selectList(new LambdaQueryWrapper<DeviceBaseInfoDO>()
                .eq(DeviceBaseInfoDO::getOrgFactoryId, factoryId));
    }

    @Override
    public PageResult<DeviceBaseInfoDO> selectPage(DeviceBaseInfoPageQuery query) {
        Page<DeviceBaseInfoDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        // 注意：不再按租户过滤，device_info 表已删除 tenant_uuid 字段
        LambdaQueryWrapper<DeviceBaseInfoDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getDeviceCodeLike())) {
            wrapper.like(DeviceBaseInfoDO::getDeviceCode, query.getDeviceCodeLike());
        }
        if (StringUtils.isNotBlank(query.getDeviceNameLike())) {
            wrapper.like(DeviceBaseInfoDO::getDeviceName, query.getDeviceNameLike());
        }
        if (query.getDeviceTypeCodes() != null && !query.getDeviceTypeCodes().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceTypeCode, query.getDeviceTypeCodes());
        }
        if (query.getDeviceSubTypeNames() != null && !query.getDeviceSubTypeNames().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceSubTypeName, query.getDeviceSubTypeNames());
        }
        if (query.getDeviceModelIds() != null && !query.getDeviceModelIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getDeviceModelId, query.getDeviceModelIds());
        }
        if (query.getOrgFactoryIds() != null && !query.getOrgFactoryIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getOrgFactoryId, query.getOrgFactoryIds());
        }
        if (query.getOrgWorkshopIds() != null && !query.getOrgWorkshopIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getOrgWorkshopId, query.getOrgWorkshopIds());
        }
        if (query.getOrgProductionLineIds() != null && !query.getOrgProductionLineIds().isEmpty()) {
            wrapper.in(DeviceBaseInfoDO::getOrgProductionLineId, query.getOrgProductionLineIds());
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
    public boolean deleteById(String id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceBaseInfoDO>().eq(DeviceBaseInfoDO::getId, id)) > 0;
    }

    private void applySort(LambdaQueryWrapper<DeviceBaseInfoDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderBy(true, false, DeviceBaseInfoDO::getCreateTime);
            return;
        }
        
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        // 根据排序字段应用相应的排序规则
        switch (sortBy) {
            case "deviceCode" -> wrapper.orderBy(true, asc, DeviceBaseInfoDO::getDeviceCode);
            case "deviceName" -> wrapper.orderBy(true, asc, DeviceBaseInfoDO::getDeviceName);
            case "updatedTime" -> wrapper.orderBy(true, asc, DeviceBaseInfoDO::getUpdateTime);
            default -> wrapper.orderBy(true, false, DeviceBaseInfoDO::getCreateTime);
        }
    }
}

