package com.weili.iot_portal.dal.repository.devicebase.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicebase.DeviceLocationDO;
import com.weili.iot_portal.dal.mapper.devicebase.DeviceLocationMapper;
import com.weili.iot_portal.dal.repository.devicebase.DeviceLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DeviceLocationRepositoryImpl implements DeviceLocationRepository {

    private final DeviceLocationMapper mapper;

    @Override
    public Optional<DeviceLocationDO> findByDeviceId(String tenantId, String deviceId) {
        return Optional.ofNullable(mapper.selectOne(scope(tenantId).eq(DeviceLocationDO::getDeviceInfoId, deviceId)
                .eq(DeviceLocationDO::getActive, Boolean.TRUE)));
    }

    @Override
    public List<DeviceLocationDO> findByDeviceIds(String tenantId, List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(scope(tenantId)
                .in(DeviceLocationDO::getDeviceInfoId, deviceIds)
                .eq(DeviceLocationDO::getActive, Boolean.TRUE));
    }

    @Override
    public void insert(DeviceLocationDO entity) {
        mapper.insert(entity);
    }

    @Override
    public void update(DeviceLocationDO entity) {
        mapper.updateById(entity);
    }

    @Override
    public List<String> findDeviceIdsByLocationCode(String tenantId, String locationCode) {
        if (locationCode == null) {
            return Collections.emptyList();
        }
        return mapper.selectList(scope(tenantId)
                        .eq(DeviceLocationDO::getLocationCode, locationCode)
                        .eq(DeviceLocationDO::getActive, Boolean.TRUE))
                .stream()
                .map(DeviceLocationDO::getDeviceInfoId)
                .collect(Collectors.toList());
    }

    private LambdaQueryWrapper<DeviceLocationDO> scope(String tenantId) {
        LambdaQueryWrapper<DeviceLocationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceLocationDO::getTenantUuid, tenantId);
        return wrapper;
    }
}

