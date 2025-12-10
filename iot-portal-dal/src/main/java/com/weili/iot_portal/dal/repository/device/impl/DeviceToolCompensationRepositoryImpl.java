package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolCompensationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceToolCompensationRepositoryImpl implements DeviceToolCompensationRepository {

    private final DeviceToolCompensationMapper mapper;


    @Override
    public DeviceToolCompensationDO findActive(String deviceId, String toolHolderNo) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(DeviceToolCompensationDO::getToolHolderNo, toolHolderNo)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByDesc(DeviceToolCompensationDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public List<DeviceToolCompensationDO> findActiveByDevice(String factoryId, String deviceId) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(StringUtils.isNotBlank(factoryId), DeviceToolCompensationDO::getOrgFactoryId, factoryId)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByAsc(DeviceToolCompensationDO::getToolHolderNo);
        return mapper.selectList(wrapper);
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


