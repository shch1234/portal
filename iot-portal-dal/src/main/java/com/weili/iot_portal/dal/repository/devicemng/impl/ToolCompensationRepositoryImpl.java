package com.weili.iot_portal.dal.repository.devicemng.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weili.iot_portal.dal.dataobject.devicemng.ToolCompensationDO;
import com.weili.iot_portal.dal.mapper.devicemng.ToolCompensationMapper;
import com.weili.iot_portal.dal.repository.devicemng.ToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ToolCompensationRepositoryImpl implements ToolCompensationRepository {

    private final ToolCompensationMapper mapper;

    @Override
    public ToolCompensationDO findActive(String deviceId, String toolHolderNo) {
        LambdaQueryWrapper<ToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(ToolCompensationDO::getToolHolderNo, toolHolderNo)
                .eq(ToolCompensationDO::getActive, 1)
                .orderByDesc(ToolCompensationDO::getStartTs)
                .last("LIMIT 1");
        return mapper.selectOne(wrapper);
    }

    @Override
    public java.util.List<ToolCompensationDO> findActiveByDevice(String factoryId, String deviceId) {
        LambdaQueryWrapper<ToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(StringUtils.isNotBlank(factoryId), ToolCompensationDO::getOrgFactoryId, factoryId)
                .eq(ToolCompensationDO::getActive, 1)
                .orderByAsc(ToolCompensationDO::getToolHolderNo);
        return mapper.selectList(wrapper);
    }

    @Override
    public void insert(ToolCompensationDO record) {
        if (record == null) {
            return;
        }
        mapper.insert(record);
    }

    @Override
    public void updateById(ToolCompensationDO record) {
        if (record == null || record.getId() == null) {
            return;
        }
        mapper.updateById(record);
    }
}


