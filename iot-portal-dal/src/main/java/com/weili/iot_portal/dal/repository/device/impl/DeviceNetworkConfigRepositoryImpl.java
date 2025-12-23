package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weili.basic.common.model.PageResult;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceNetworkConfigDO;
import com.weili.iot_portal.dal.ddd.device.DeviceNetworkConfigPageQuery;
import com.weili.iot_portal.dal.mapper.device.DeviceNetworkConfigMapper;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceNetworkConfigRepository;
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
    private final DeviceInfoRepository deviceInfoRepository;

    @Override
    public Optional<DeviceNetworkConfigDO> findById(Long id) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceNetworkConfigDO>()
                .eq(DeviceNetworkConfigDO::getId, id)));
    }

    @Override
    public Optional<DeviceNetworkConfigDO> findByDeviceInfoId(Long deviceInfoId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceNetworkConfigDO>()
                .eq(DeviceNetworkConfigDO::getDeviceInfoId, deviceInfoId)
                .eq(DeviceNetworkConfigDO::getIsActive, true)));
    }

    @Override
    public boolean existsByDeviceInfoId(String deviceInfoId, String excludeId) {
        LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper = new LambdaQueryWrapper<DeviceNetworkConfigDO>()
                .eq(DeviceNetworkConfigDO::getDeviceInfoId, deviceInfoId);
        if (StringUtils.isNotBlank(excludeId)) {
            wrapper.ne(DeviceNetworkConfigDO::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public PageResult<DeviceNetworkConfigDO> selectPage(DeviceNetworkConfigPageQuery query) {
        LambdaQueryWrapper<DeviceNetworkConfigDO> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(query.getFactoryId())) {
            List<Long> deviceIds = deviceInfoRepository.findByFactoryId(query.getFactoryId())
                    .stream()
                    .map(DeviceInfoDO::getId)
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
    public boolean deleteById(Long id) {
        return mapper.delete(new LambdaQueryWrapper<DeviceNetworkConfigDO>()
                .eq(DeviceNetworkConfigDO::getId, id)) > 0;
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

