package com.weili.iot_portal.dal.repository.devicebase.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceBaseInfoDO;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceNetworkConfigMapper;
import com.weili.iot_portal.dal.repository.devicebase.DeviceBaseInfoRepository;
import com.weili.iot_portal.dal.repository.devicebase.DeviceNetworkConfigRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DeviceNetworkConfigRepositoryImpl implements DeviceNetworkConfigRepository {

    private final DeviceNetworkConfigMapper mapper;
    private final DeviceBaseInfoRepository deviceBaseInfoRepository;

    @Override
    public Optional<DeviceNetworkConfigDO> findById(String tenantId, String id) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceNetworkConfigDO::getId, id)));
    }

    @Override
    public Optional<DeviceNetworkConfigDO> findByDeviceInfoId(String tenantId, String deviceInfoId) {
        return Optional.ofNullable(mapper.selectOne(tenantScope(tenantId).eq(DeviceNetworkConfigDO::getDeviceInfoId, deviceInfoId)));
    }

    @Override
    public boolean existsByDeviceInfoId(String tenantId, String deviceInfoId, String excludeId) {
        LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper = tenantScope(tenantId)
                .eq(DeviceNetworkConfigDO::getDeviceInfoId, deviceInfoId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceNetworkConfigDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceNetworkConfigDO> selectPage(DeviceNetworkConfigPageQuery query) {
        LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper = tenantScope(query.getTenantId());

        if (StringUtils.isNotBlank(query.getFactoryId())) {
            List<String> deviceIds = deviceBaseInfoRepository.findByFactoryId(query.getTenantId(), query.getFactoryId())
                    .stream()
                    .map(DeviceBaseInfoDO::getId)
                    .collect(Collectors.toList());
            if (deviceIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L);
            }
            wrapper.in(DeviceNetworkConfigDO::getDeviceInfoId, deviceIds);
        }

        if (StringUtils.isNotBlank(query.getIpAddress())) {
            wrapper.eq(DeviceNetworkConfigDO::getIpAddress, query.getIpAddress());
        }
        if (StringUtils.isNotBlank(query.getProtocol())) {
            wrapper.eq(DeviceNetworkConfigDO::getProtocol, query.getProtocol());
        }
        if (query.getDeviceIds() != null && !query.getDeviceIds().isEmpty()) {
            wrapper.in(DeviceNetworkConfigDO::getDeviceInfoId, query.getDeviceIds());
        }

        applySort(wrapper, query.getSortBy(), query.getSortDirection());

        Page<DeviceNetworkConfigDO> page = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DeviceNetworkConfigDO> result = mapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Override
    public void insert(DeviceNetworkConfigDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceNetworkConfigDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public boolean deleteById(String tenantId, String id) {
        return mapper.delete(tenantScope(tenantId).eq(DeviceNetworkConfigDO::getId, id)) > 0;
    }

    private LambdaQueryWrapper<DeviceNetworkConfigDO> tenantScope(String tenantId) {
        LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceNetworkConfigDO::getTenantUuid, tenantId);
        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper, String sortBy, String sortDirection) {
        // 如果没有指定排序字段，默认按创建时间降序排列
        if (StringUtils.isBlank(sortBy)) {
            wrapper.orderByDesc(DeviceNetworkConfigDO::getCreateTime);
            return;
        }

        // 解析排序方向，默认为升序
        boolean isAscending = !"desc".equalsIgnoreCase(sortDirection);

        // 根据不同的排序字段应用相应的排序规则
        wrapper.orderBy(true, isAscending, getSortFieldFunction(sortBy));
    }

    private SFunction<DeviceNetworkConfigDO, ?> getSortFieldFunction(String sortBy) {
        return switch (sortBy) {
            case "ipAddress" -> DeviceNetworkConfigDO::getIpAddress;
            case "protocol" -> DeviceNetworkConfigDO::getProtocol;
            case "updatedTime" -> DeviceNetworkConfigDO::getUpdateTime;
            default -> DeviceNetworkConfigDO::getCreateTime;
        };
    }
}

