package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolCompensationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
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

    @Override
    public void deactivateById(Long id, Long endTs, Integer active) {
        if (id == null) {
            return;
        }
        
        // 先查询记录，获取 device_info_id, org_factory_id, tool_holder_no
        DeviceToolCompensationDO record = mapper.selectById(id);
        if (record == null) {
            log.warn("[DeviceToolCompensationRepository] 记录不存在，无法关闭: id={}", id);
            return;
        }
        
        log.debug("[DeviceToolCompensationRepository] 准备关闭记录: id={}, deviceInfoId={}, orgFactoryId={}, toolHolderNo={}, active={}",
                id, record.getDeviceInfoId(), record.getOrgFactoryId(), record.getToolHolderNo(), active);
        
        // 在更新前，先删除已存在的 active=0 的记录（避免唯一约束冲突）
        // 唯一约束 uq_tool_comp_active 基于 (device_info_id, tool_holder_no, active)，不包含 org_factory_id
        // 如果已存在 active=0 的记录，更新当前记录为 active=0 会违反唯一约束
        LambdaQueryWrapper<DeviceToolCompensationDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, record.getDeviceInfoId())
                .eq(DeviceToolCompensationDO::getToolHolderNo, record.getToolHolderNo())
                .eq(DeviceToolCompensationDO::getActive, 0)
                .ne(DeviceToolCompensationDO::getId, id); // 排除当前记录
        
        int deletedCount = mapper.delete(deleteWrapper);
        if (deletedCount > 0) {
            log.info("[DeviceToolCompensationRepository] 删除已存在的 active=0 记录，避免唯一约束冲突: " +
                    "deviceInfoId={}, toolHolderNo={}, deletedCount={} (唯一约束基于 device_info_id, tool_holder_no, active)",
                    record.getDeviceInfoId(), record.getToolHolderNo(), deletedCount);
        }
        
        // 然后更新当前记录
        LambdaUpdateWrapper<DeviceToolCompensationDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DeviceToolCompensationDO::getId, id)
                .set(DeviceToolCompensationDO::getEndTs, endTs)
                .set(DeviceToolCompensationDO::getActive, active);
        int updatedCount = mapper.update(null, updateWrapper);
        
        if (updatedCount > 0) {
            log.debug("[DeviceToolCompensationRepository] 成功关闭记录: id={}, endTs={}, active={}",
                    id, endTs, active);
        } else {
            log.warn("[DeviceToolCompensationRepository] 更新记录失败: id={}, endTs={}, active={}",
                    id, endTs, active);
        }
    }
}


