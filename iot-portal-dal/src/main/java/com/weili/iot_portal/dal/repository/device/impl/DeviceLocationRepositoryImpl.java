package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceLocationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceLocationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceLocationRepository;
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
    public Optional<DeviceLocationDO> findByDeviceId(String deviceId) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<DeviceLocationDO>()
                .eq(DeviceLocationDO::getDeviceInfoId, deviceId)
                .eq(DeviceLocationDO::getActive, Boolean.TRUE)));
    }

    @Override
    public List<DeviceLocationDO> findByDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectList(new LambdaQueryWrapper<DeviceLocationDO>()
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
    public List<String> findDeviceIdsByLocationCode(String locationCode) {
        if (locationCode == null) {
            return Collections.emptyList();
        }
        return mapper.selectList(new LambdaQueryWrapper<DeviceLocationDO>()
                        .eq(DeviceLocationDO::getLocationCode, locationCode)
                        .eq(DeviceLocationDO::getActive, Boolean.TRUE))
                .stream()
                .map(DeviceLocationDO::getDeviceInfoId)
                .collect(Collectors.toList());
    }
}

