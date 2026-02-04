package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.device.IDeviceStateRecordRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备状态记录修复服务实现
 * 用于修复历史遗留的异常记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateRecordRepairService implements IDeviceStateRecordRepairService {

    private final DeviceStateRecordRepository stateRecordRepository;
    private final DeviceLockService deviceLockService;
    
    /**
     * 修复操作的锁超时时间（秒）
     * 使用较短的超时时间，避免与实时事件处理长时间竞争
     */
    private static final int REPAIR_LOCK_TIMEOUT_SECONDS = 3;

    /**
     * 修复历史遗留的未结束记录
     * 将未结束且开始时间早于指定时间的记录标记为已结束（使用 start_ts + 1毫秒）
     * 并在 properties 中标记为有问题
     * <p>
     * 优化：使用分布式锁避免与实时事件处理冲突
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 10)
    public boolean repairHistoricalOngoingRecord(DeviceStateRecordDO record, long beforeTime) {
        if (record == null || record.getId() == null) {
            return false;
        }

        Long deviceId = record.getDeviceInfoId();
        if (deviceId == null) {
            return false;
        }

        // 检查是否需要修复
        if (record.getEndTs() != null) {
            // 已经结束，不需要修复
            return false;
        }

        if (record.getStartTs() == null || record.getStartTs() >= beforeTime) {
            // 开始时间不早于指定时间，不需要修复
            return false;
        }

        // 使用分布式锁避免与实时事件处理冲突
        if (!deviceLockService.tryLockState(deviceId, REPAIR_LOCK_TIMEOUT_SECONDS)) {
            log.debug("修复历史遗留记录：获取设备状态锁失败，跳过: deviceId={}, recordId={}",
                    deviceId, record.getId());
            return false; // 实时事件正在处理，跳过修复
        }

        try {
            // 重新查询记录，确保是最新的（可能已被实时事件处理）
            Optional<DeviceStateRecordDO> latestOpt = stateRecordRepository.findLatestState(deviceId);
            
            if (latestOpt.isEmpty()) {
                log.debug("修复历史遗留记录：记录不存在，可能已被删除: deviceId={}, oldRecordId={}",
                        deviceId, record.getId());
                return false;
            }
            
            DeviceStateRecordDO latestRecord = latestOpt.get();
            
            // 检查记录ID是否匹配（如果不匹配，说明记录已被实时事件处理）
            if (!latestRecord.getId().equals(record.getId())) {
                log.debug("修复历史遗留记录：记录已被实时事件处理，跳过: deviceId={}, oldId={}, newId={}",
                        deviceId, record.getId(), latestRecord.getId());
                return false;
            }
            
            // 再次检查是否需要修复（可能已被实时事件处理）
            if (latestRecord.getEndTs() != null) {
                log.debug("修复历史遗留记录：记录已结束，不需要修复: deviceId={}, recordId={}",
                        deviceId, latestRecord.getId());
                return false;
            }

            // 设置结束时间为开始时间 + 1毫秒（表示零时长记录，标记为异常）
            long repairedEndTs = latestRecord.getStartTs() + 1;
            latestRecord.setEndTs(repairedEndTs);
            latestRecord.setDurationS(1L); // 1毫秒

            // 在 properties 中标记为有问题
            Map<String, Object> properties = latestRecord.getProperties();
            if (properties == null) {
                properties = new HashMap<>();
            } else {
                properties = new HashMap<>(properties);
            }
            properties.put("repaired", true);
            properties.put("repair_reason", "历史遗留的未结束记录，已自动修复");
            properties.put("repair_time", System.currentTimeMillis());
            properties.put("original_end_ts", null); // 记录原始状态（未结束）
            latestRecord.setProperties(properties);

            // 更新记录（捕获数据库锁超时异常）
            try {
                stateRecordRepository.update(latestRecord);
                log.info("修复历史遗留的未结束记录: recordId={}, deviceId={}, startTs={}, repairedEndTs={}",
                        latestRecord.getId(), deviceId, latestRecord.getStartTs(), repairedEndTs);
                return true;
            } catch (CannotAcquireLockException e) {
                // 数据库行锁超时：可能与其他事务冲突，记录警告但不抛出异常
                log.warn("修复历史遗留记录：数据库锁超时，跳过: recordId={}, deviceId={}, error={}",
                        latestRecord.getId(), deviceId, e.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("修复历史遗留记录失败: recordId={}, deviceId={}, error={}",
                    record.getId(), deviceId, e.getMessage(), e);
            return false;
        } finally {
            // 释放分布式锁
            deviceLockService.unlockState(deviceId);
        }
    }

    /**
     * 批量修复历史遗留的未结束记录
     * <p>
     * 注意：不使用 @Transactional，让每个修复使用独立事务（repairHistoricalOngoingRecord 内部已有事务）
     * 这样可以避免批量修复失败导致所有修复回滚
     * </p>
     */
    @Override
    public int batchRepairHistoricalOngoingRecords(long beforeTime, int batchSize) {
        log.info("开始批量修复历史遗留的未结束记录: beforeTime={}, batchSize={}", beforeTime, batchSize);

        int totalRepaired = 0;
        int batchCount = 0;

        while (true) {
            // 查询未结束的记录（开始时间早于指定时间）
            // findAllOngoing 的第一个参数是 startTsAfter，表示查询 start_ts >= startTsAfter 的记录
            // 我们需要查询所有未结束的记录，所以传入一个很早的时间
            List<DeviceStateRecordDO> ongoingRecords = stateRecordRepository.findAllOngoing(
                    beforeTime - (365L * 24 * 60 * 60 * 1000), // 查询一年前的记录作为起点
                    batchSize);

            if (ongoingRecords.isEmpty()) {
                break;
            }

            batchCount++;
            int batchRepaired = 0;

            for (DeviceStateRecordDO record : ongoingRecords) {
                // 只修复开始时间早于指定时间的记录
                if (record.getStartTs() != null && record.getStartTs() < beforeTime) {
                    if (repairHistoricalOngoingRecord(record, beforeTime)) {
                        batchRepaired++;
                        totalRepaired++;
                    }
                }
            }

            log.info("批量修复进度: 第{}批, 本批修复{}条, 累计修复{}条", 
                    batchCount, batchRepaired, totalRepaired);

            // 如果本批记录数小于batchSize，说明已经处理完所有记录
            if (ongoingRecords.size() < batchSize) {
                break;
            }
        }

        log.info("批量修复完成: 共修复{}条历史遗留的未结束记录", totalRepaired);
        return totalRepaired;
    }
}
