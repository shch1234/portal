package com.weili.iot_portal.business.device_base.dal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceBaseInfoDO;
import com.weili.iot_portal.business.device_base.dal.dataobject.DeviceConfigurationDO;
import com.weili.iot_portal.business.device_base.dal.ddd.DeviceConfigurationPageQuery;
import com.weili.iot_portal.business.device_base.dal.mapper.DeviceConfigurationMapper;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceBaseInfoRepository;
import com.weili.iot_portal.business.device_base.dal.repository.DeviceConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 设备配置仓储实现
 */
@Repository
@RequiredArgsConstructor
public class DeviceConfigurationRepositoryImpl implements DeviceConfigurationRepository {

    private final DeviceConfigurationMapper mapper;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    @Override
    public Optional<DeviceConfigurationDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceConfigurationDO::getId, id)));
    }

    @Override
    public Optional<DeviceConfigurationDO> findByDeviceId(String tenantId, String deviceId) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceConfigurationDO::getDeviceId, deviceId)));
    }

    @Override
    public boolean existsByDeviceId(String tenantId, String deviceId, String excludeId) {
        LambdaQueryWrapper<DeviceConfigurationDO> wrapper = tenantScope(tenantId)
                .eq(DeviceConfigurationDO::getDeviceId, deviceId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceConfigurationDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceConfigurationDO> selectPage(DeviceConfigurationPageQuery query) {
        LambdaQueryWrapper<DeviceConfigurationDO> wrapper = tenantScope(query.getTenantId());

        if (StringUtils.isNotBlank(query.getFactoryId())) {
            List<String> deviceIds = deviceBaseInfoRepository.findByFactoryId(query.getTenantId(), query.getFactoryId())
                    .stream()
                    .map(DeviceBaseInfoDO::getId)
                    .collect(Collectors.toList());
            if (deviceIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
            wrapper.in(DeviceConfigurationDO::getDeviceId, deviceIds);
        }

        if (StringUtils.isNotBlank(query.getIpAddress())) {
            wrapper.eq(DeviceConfigurationDO::getIpAddress, query.getIpAddress());
        }
        if (StringUtils.isNotBlank(query.getProtocol())) {
            wrapper.eq(DeviceConfigurationDO::getProtocol, query.getProtocol());
        }
        if (StringUtils.isNotBlank(query.getLocationCode())) {
            wrapper.eq(DeviceConfigurationDO::getLocationCode, query.getLocationCode());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceConfigurationDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceConfigurationDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceConfigurationDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceConfigurationDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(DeviceConfigurationDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceConfigurationDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<DeviceConfigurationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceConfigurationDO::getTenantId, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceConfigurationDO> wrapper, String sortBy, String sortDirection) {
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceConfigurationDO::getCreateTime);
            return;
        }
        boolean asc = !"desc".equalsIgnoreCase(sortDirection);
        wrapper.orderBy(true, asc, switch (sortBy) {
            case "ipAddress" -> DeviceConfigurationDO::getIpAddress;
            case "protocol" -> DeviceConfigurationDO::getProtocol;
            case "locationCode" -> DeviceConfigurationDO::getLocationCode;
            case "updatedTime" -> DeviceConfigurationDO::getUpdateTime;
            default -> DeviceConfigurationDO::getCreateTime;
        });
    }
}

