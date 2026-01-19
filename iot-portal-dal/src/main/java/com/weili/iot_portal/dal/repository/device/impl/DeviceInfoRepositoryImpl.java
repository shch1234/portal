package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.ddd.device.DeviceBaseInfoPageQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceInfoMapper;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
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
    public Optional<DeviceInfoDO> findById(Long id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getId, id)));
    }

    @Override
    public Optional<DeviceInfoDO> findByDeviceCode(String deviceCode) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeviceCode, deviceCode)));
    }

    @Override
    public boolean existsByDeviceCode(String deviceCode, Long excludeId) {
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeviceCode, deviceCode);
        if (excludeId!=null) {
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
    public List<DeviceInfoDO> selectByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<DeviceInfoDO>()
                .in(DeviceInfoDO::getId, ids));
    }

    @Override
    public List<DeviceInfoDO> findMonitoredDevices(Long factoryId) {
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<DeviceInfoDO>()
                .eq(DeviceInfoDO::getDeleted, false);

        if (factoryId != null) {
            wrapper.eq(DeviceInfoDO::getOrgFactoryId, factoryId);
        }

        return mapper.selectList(wrapper);
    }

    @Override
    public PageResult<DeviceInfoDO> selectPage(DeviceBaseInfoPageQuery query) {
        Page<DeviceInfoDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<DeviceInfoDO> wrapper = new LambdaQueryWrapper<>();
        
        // 如果指定了deviceIds，只使用deviceIds进行筛选（用于hasAlarm筛选后的设备ID列表）
        // 其他筛选条件已经在查询所有设备时应用过了
        if (query.getDeviceIds() != null && !query.getDeviceIds().isEmpty()) {
            wrapper.in(DeviceInfoDO::getId, query.getDeviceIds());
        } else {
            // 如果没有指定deviceIds，应用所有筛选条件
            if (StringUtils.isNotBlank(query.getDeviceCode())) {
                wrapper.like(DeviceInfoDO::getDeviceCode, query.getDeviceCode());
            }
            if (StringUtils.isNotBlank(query.getDeviceName())) {
                wrapper.like(DeviceInfoDO::getDeviceName, query.getDeviceName());
            }
            if (query.getDeviceTypeCodes() != null && !query.getDeviceTypeCodes().isEmpty()) {
                wrapper.in(DeviceInfoDO::getDeviceTypeCode, query.getDeviceTypeCodes());
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
    public boolean deleteById(Long id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceInfoDO>().eq(DeviceInfoDO::getId, id)) > 0;
    }

    private void applySort(LambdaQueryWrapper<DeviceInfoDO> wrapper, String sortBy, String sortDirection) {
        // 始终先按是否监控排序，监控中的排在前面（降序：true在前）
        wrapper.orderByDesc(DeviceInfoDO::getIsMonitored);
        
        if (StringUtils.isBlank(sortBy)) {
            // 如果没有指定排序字段，则按创建时间升序作为次要排序（后创建的排在后面）
            wrapper.orderByAsc(DeviceInfoDO::getCreateTime);
            return;
        }

        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        switch (sortBy) {
            case "deviceCode" -> wrapper.orderBy(true, asc, DeviceInfoDO::getDeviceCode);
            case "deviceName" -> wrapper.orderBy(true, asc, DeviceInfoDO::getDeviceName);
            case "updatedTime" -> wrapper.orderBy(true, asc, DeviceInfoDO::getUpdateTime);
            case "createTime" -> wrapper.orderBy(true, asc, DeviceInfoDO::getCreateTime);
            default -> wrapper.orderByAsc(DeviceInfoDO::getCreateTime);  // 默认按创建时间升序（后创建的排在后面）
        }
    }
}

