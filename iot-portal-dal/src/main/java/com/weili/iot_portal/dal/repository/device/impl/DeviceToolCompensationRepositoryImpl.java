package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolCompensationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceToolCompensationRepositoryImpl implements DeviceToolCompensationRepository {

    private final DeviceToolCompensationMapper mapper;


    @Override
    public DeviceToolCompensationDO findActive(Long deviceId, String toolHolderNo) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(DeviceToolCompensationDO::getToolHolderNo, toolHolderNo)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByDesc(DeviceToolCompensationDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public List<DeviceToolCompensationDO> findActiveByDevice(Long factoryId, String deviceId) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(factoryId != null, DeviceToolCompensationDO::getOrgFactoryId, factoryId)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByAsc(DeviceToolCompensationDO::getToolHolderNo);
        return mapper.selectList(wrapper);
    }

    @Override
    public List<DeviceToolCompensationDO> findActiveByDeviceWithPage(Long factoryId, String deviceId, Integer offset, Integer limit) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(factoryId != null, DeviceToolCompensationDO::getOrgFactoryId, factoryId)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByAsc(DeviceToolCompensationDO::getToolHolderNo)
                .last("LIMIT " + limit + " OFFSET " + offset);
        return mapper.selectList(wrapper);
    }

    @Override
    public Long countActiveByDevice(Long factoryId, String deviceId) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(factoryId != null, DeviceToolCompensationDO::getOrgFactoryId, factoryId)
                .eq(DeviceToolCompensationDO::getActive, 1);
        return mapper.selectCount(wrapper);
    }

    @Override
    public void insert(DeviceToolCompensationDO record) {
        if (record == null) {
            return;
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(DeviceToolCompensationDO record) {
        if (record == null || record.getId() == null) {
            return;
        }
        mapper.updateById(record);
    }
}


