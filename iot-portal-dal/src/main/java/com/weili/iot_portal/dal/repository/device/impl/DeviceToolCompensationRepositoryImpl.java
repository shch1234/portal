package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolCompensationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    public DeviceToolCompensationDO findActiveWithLock(Long deviceId, String toolHolderNo) {
        LambdaQueryWrapper<DeviceToolCompensationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, deviceId)
                .eq(DeviceToolCompensationDO::getToolHolderNo, toolHolderNo)
                .eq(DeviceToolCompensationDO::getActive, 1)
                .orderByDesc(DeviceToolCompensationDO::getStartTs)
                .last("LIMIT 1 FOR UPDATE");
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
        
        // 在插入前，先删除已存在的相同唯一约束的记录（避免唯一约束冲突）
        // 唯一约束 uq_tool_comp_active 基于 (device_info_id, tool_holder_no, active)
        // 需要根据 active 值删除对应的记录
        if (record.getDeviceInfoId() != null && record.getToolHolderNo() != null && record.getActive() != null) {
            LambdaQueryWrapper<DeviceToolCompensationDO> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, record.getDeviceInfoId())
                    .eq(DeviceToolCompensationDO::getToolHolderNo, record.getToolHolderNo())
                    .eq(DeviceToolCompensationDO::getActive, record.getActive());
            
            int deletedCount = mapper.delete(deleteWrapper);
            if (deletedCount > 0) {
                log.info("[DeviceToolCompensationRepository] 插入前删除已存在的记录，避免唯一约束冲突: " +
                        "deviceInfoId={}, toolHolderNo={}, active={}, deletedCount={}",
                        record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), deletedCount);
            }
        }
        
        try {
            mapper.insert(record);
            log.debug("[DeviceToolCompensationRepository] 成功插入记录: deviceInfoId={}, toolHolderNo={}, active={}, id={}",
                    record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), record.getId());
        } catch (DuplicateKeyException e) {
            // 处理并发插入导致的唯一约束冲突
            // 如果插入失败，再次尝试删除并插入（可能其他线程已插入）
            log.warn("[DeviceToolCompensationRepository] 插入时发生唯一约束冲突，尝试删除后重新插入: " +
                    "deviceInfoId={}, toolHolderNo={}, active={}, error={}",
                    record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), e.getMessage());
            
            // 再次删除可能存在的记录
            if (record.getDeviceInfoId() != null && record.getToolHolderNo() != null && record.getActive() != null) {
                LambdaQueryWrapper<DeviceToolCompensationDO> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, record.getDeviceInfoId())
                        .eq(DeviceToolCompensationDO::getToolHolderNo, record.getToolHolderNo())
                        .eq(DeviceToolCompensationDO::getActive, record.getActive());
                
                int deletedCount = mapper.delete(deleteWrapper);
                if (deletedCount > 0) {
                    log.info("[DeviceToolCompensationRepository] 重试删除冲突记录: deviceInfoId={}, toolHolderNo={}, active={}, deletedCount={}",
                            record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), deletedCount);
                }
            }
            
            // 重新插入
            mapper.insert(record);
            log.info("[DeviceToolCompensationRepository] 重试插入成功: deviceInfoId={}, toolHolderNo={}, active={}, id={}",
                    record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), record.getId());
        }
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


