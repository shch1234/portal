package com.weili.iot_portal.dal.repository.device.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolCompensationDO;
import com.weili.iot_portal.dal.mapper.device.DeviceToolCompensationMapper;
import com.weili.iot_portal.dal.repository.device.DeviceToolCompensationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
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
                // 使用 NOWAIT 避免长时间等待，如果锁被占用则立即返回 null
                // 这样可以避免锁等待超时，由分布式锁和重试机制来处理并发
                .last("LIMIT 1 FOR UPDATE NOWAIT");
        try {
            return mapper.selectOne(wrapper);
        } catch (org.springframework.dao.CannotAcquireLockException e) {
            // 如果无法获取锁（NOWAIT），返回 null，由调用方处理
            log.debug("[DeviceToolCompensationRepository] 无法获取行锁（NOWAIT）: deviceId={}, toolHolderNo={}", 
                    deviceId, toolHolderNo);
            return null;
        }
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
        
        // 优化：只删除 active=1 的记录，保留 active=0 的历史版本
        // 如果插入的是 active=1 的记录，需要先删除已存在的 active=1 记录（确保唯一性）
        // 如果插入的是 active=0 的记录，不需要删除（允许多条历史版本）
        if (record.getDeviceInfoId() != null && record.getToolHolderNo() != null 
                && record.getActive() != null && record.getActive() == 1) {
            // 只删除 active=1 的记录，保留 active=0 的历史版本
            LambdaQueryWrapper<DeviceToolCompensationDO> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, record.getDeviceInfoId())
                    .eq(DeviceToolCompensationDO::getToolHolderNo, record.getToolHolderNo())
                    .eq(DeviceToolCompensationDO::getActive, 1);
            
            int deletedCount = mapper.delete(deleteWrapper);
            if (deletedCount > 0) {
                log.info("[DeviceToolCompensationRepository] 插入前删除已存在的 active=1 记录，确保唯一性: " +
                        "deviceInfoId={}, toolHolderNo={}, deletedCount={}",
                        record.getDeviceInfoId(), record.getToolHolderNo(), deletedCount);
            }
        }
        
        try {
            mapper.insert(record);
            log.debug("[DeviceToolCompensationRepository] 成功插入记录: deviceInfoId={}, toolHolderNo={}, active={}, id={}",
                    record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), record.getId());
        } catch (DuplicateKeyException e) {
            // 处理并发插入导致的唯一约束冲突（如果唯一约束还存在）
            // 如果插入失败，再次尝试删除并插入（可能其他线程已插入）
            log.warn("[DeviceToolCompensationRepository] 插入时发生唯一约束冲突，尝试删除后重新插入: " +
                    "deviceInfoId={}, toolHolderNo={}, active={}, error={}",
                    record.getDeviceInfoId(), record.getToolHolderNo(), record.getActive(), e.getMessage());
            
            // 再次删除可能存在的 active=1 记录（只针对 active=1 的记录）
            if (record.getDeviceInfoId() != null && record.getToolHolderNo() != null 
                    && record.getActive() != null && record.getActive() == 1) {
                LambdaQueryWrapper<DeviceToolCompensationDO> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(DeviceToolCompensationDO::getDeviceInfoId, record.getDeviceInfoId())
                        .eq(DeviceToolCompensationDO::getToolHolderNo, record.getToolHolderNo())
                        .eq(DeviceToolCompensationDO::getActive, 1);
                
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
        
        // 优化：直接更新当前记录为 active=0，保留所有历史版本
        // 不再删除已存在的 active=0 记录，以支持版本管理
        // 通过应用层的分布式锁和行锁确保 active=1 的记录唯一性
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


