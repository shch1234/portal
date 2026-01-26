package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.device.IDeviceStateShiftSplitService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceStateEventFields;
import com.weili.iot_portal.service.record.TimeRangeRecordHandler;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备状态跨班次拆分服务实现
 * <p>
 * 功能：定期检查进行中的状态记录，如果跨班次则自动拆分
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStateShiftSplitService implements IDeviceStateShiftSplitService {

    private final DeviceStateRecordRepository stateRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final TimeRangeRecordHandler timeRangeRecordHandler;
    private final DeviceLockService deviceLockService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftSplitResult processCrossShiftSplit(Long startTsAfter, Integer batchSize) {
        long currentTime = System.currentTimeMillis();
        
        // 默认只处理最近24小时内的记录（避免扫描过旧的数据）
        if (startTsAfter == null) {
            startTsAfter = currentTime - (24L * 60 * 60 * 1000); // 24小时前
        }
        
        // 默认每批处理100条记录
        if (batchSize == null || batchSize <= 0) {
            batchSize = 100;
        }

        log.info("[DeviceStateShiftSplitService] 开始处理跨班次拆分: startTsAfter={}, batchSize={}", 
                startTsAfter, batchSize);

        int totalProcessed = 0;
        int splitCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        // 分批处理，避免一次性加载过多数据
        while (true) {
            List<DeviceStateRecordDO> ongoingRecords = stateRecordRepository.findAllOngoing(startTsAfter, batchSize);
            
            if (ongoingRecords.isEmpty()) {
                break; // 没有更多记录需要处理
            }

            log.debug("[DeviceStateShiftSplitService] 本批查询到{}条进行中记录", ongoingRecords.size());

            for (DeviceStateRecordDO record : ongoingRecords) {
                try {
                    // 使用分布式锁保护拆分操作，避免与事件处理或其他定时任务实例冲突
                    if (shouldSplit(record, currentTime)) {
                        boolean splitSuccess = splitRecordWithLock(record, currentTime);
                        if (splitSuccess) {
                            splitCount++;
                            log.debug("[DeviceStateShiftSplitService] 拆分记录: deviceId={}, startTs={}, id={}", 
                                    record.getDeviceInfoId(), record.getStartTs(), record.getId());
                        } else {
                            skipCount++;
                            log.debug("[DeviceStateShiftSplitService] 获取锁失败或记录已被处理，跳过: deviceId={}, startTs={}, id={}", 
                                    record.getDeviceInfoId(), record.getStartTs(), record.getId());
                        }
                    } else {
                        skipCount++;
                    }
                    totalProcessed++;
                } catch (Exception e) {
                    errorCount++;
                    log.error("[DeviceStateShiftSplitService] 处理记录失败: deviceId={}, startTs={}, id={}, error={}", 
                            record.getDeviceInfoId(), record.getStartTs(), record.getId(), e.getMessage(), e);
                }
            }

            // 如果本批记录数小于batchSize，说明已经处理完所有记录
            if (ongoingRecords.size() < batchSize) {
                break;
            }
        }

        log.info("[DeviceStateShiftSplitService] 跨班次拆分处理完成: 总处理={}, 拆分={}, 跳过={}, 错误={}", 
                totalProcessed, splitCount, skipCount, errorCount);

        return ShiftSplitResult.of(totalProcessed, splitCount, skipCount, errorCount);
    }

    /**
     * 判断记录是否需要拆分
     */
    private boolean shouldSplit(DeviceStateRecordDO record, long currentTime) {
        Long startTs = record.getStartTs();
        if (startTs == null) {
            return false;
        }

        try {
            // 获取开始时间所在的班次
            ShiftTimeRange startShift = shiftCalculationService.calculateShiftRange(
                    record.getOrgFactoryId(), record.getDeviceInfoId(), startTs);
            
            if (startShift == null || startShift.getEndTs() == null) {
                log.debug("[DeviceStateShiftSplitService] 无法计算班次范围，跳过: deviceId={}, startTs={}", 
                        record.getDeviceInfoId(), startTs);
                return false;
            }

            // 检查当前时间是否超过了开始班次的结束时间
            // 如果超过，说明跨班次，需要拆分
            boolean crossesShift = currentTime > startShift.getEndTs();
            
            if (crossesShift) {
                log.debug("[DeviceStateShiftSplitService] 检测到跨班次: deviceId={}, startTs={}, shiftEndTs={}, currentTime={}", 
                        record.getDeviceInfoId(), startTs, startShift.getEndTs(), currentTime);
            }
            
            return crossesShift;
        } catch (Exception e) {
            log.warn("[DeviceStateShiftSplitService] 判断是否需要拆分时发生异常: deviceId={}, startTs={}, error={}", 
                    record.getDeviceInfoId(), startTs, e.getMessage());
            return false;
        }
    }

    /**
     * 使用分布式锁拆分记录
     * <p>
     * 使用分布式锁确保同一设备的拆分操作串行化，避免：
     * 1. 多个定时任务实例同时处理同一条记录
     * 2. 定时任务与事件处理同时操作同一条记录
     * </p>
     *
     * @param record 要拆分的记录
     * @param currentTime 当前时间
     * @return true 如果拆分成功，false 如果获取锁失败或记录已被处理
     */
    private boolean splitRecordWithLock(DeviceStateRecordDO record, long currentTime) {
        Long deviceId = record.getDeviceInfoId();
        
        // 尝试获取分布式锁
        if (deviceLockService.tryLockState(deviceId, DeviceStateEventFields.LOCK_TIMEOUT_SECONDS)) {
            log.debug("[DeviceStateShiftSplitService] 获取设备状态锁失败，跳过: deviceId={}, recordId={}", 
                    deviceId, record.getId());
            return false; // 其他实例正在处理，跳过
        }

        try {
            // 重新查询记录，确保是最新的（可能已被其他实例处理）
            Optional<DeviceStateRecordDO> latestOpt = stateRecordRepository.findLatestState(deviceId);
            
            if (latestOpt.isEmpty()) {
                log.debug("[DeviceStateShiftSplitService] 记录不存在，可能已被删除: deviceId={}, oldRecordId={}", 
                        deviceId, record.getId());
                return false;
            }
            
            DeviceStateRecordDO latestRecord = latestOpt.get();
            
            // 检查记录ID是否匹配（如果不匹配，说明记录已被其他实例处理）
            if (!latestRecord.getId().equals(record.getId())) {
                log.debug("[DeviceStateShiftSplitService] 记录已被其他实例处理，跳过: deviceId={}, oldId={}, newId={}", 
                        deviceId, record.getId(), latestRecord.getId());
                return false;
            }
            
            // 重新判断是否需要拆分（可能已被其他实例拆分）
            if (!shouldSplit(latestRecord, currentTime)) {
                log.debug("[DeviceStateShiftSplitService] 记录已不需要拆分，跳过: deviceId={}, recordId={}", 
                        deviceId, latestRecord.getId());
                return false;
            }
            
            // 执行拆分
            splitRecord(latestRecord, currentTime);
            return true;
            
        } catch (Exception e) {
            log.error("[DeviceStateShiftSplitService] 拆分记录时发生异常: deviceId={}, recordId={}, error={}", 
                    deviceId, record.getId(), e.getMessage(), e);
            throw e; // 重新抛出异常，让上层处理
        } finally {
            // 释放分布式锁
            deviceLockService.unlockState(deviceId);
        }
    }

    /**
     * 拆分记录（内部方法，已获取锁）
     * <p>
     * 采用更新+插入的方式：
     * 1. 第一条记录：更新原记录的 end_ts、duration_s、is_complete 字段
     * 2. 后续记录：插入新记录
     * </p>
     */
    private void splitRecord(DeviceStateRecordDO record, long currentTime) {
        Long deviceId = record.getDeviceInfoId();
        Long orgFactoryId = record.getOrgFactoryId();
        Long startTs = record.getStartTs();
        Integer stateCode = record.getStateCode();
        Map<String, Object> properties = record.getProperties() != null 
                ? new HashMap<>(record.getProperties()) 
                : new HashMap<>();

        // 添加拆分标记
        properties.put("auto_split", true);
        properties.put("split_reason", "定时任务检测到跨班次");
        properties.put("split_time", currentTime);

        final Map<String, Object> finalProperties = properties;
        final Integer finalStateCode = stateCode;

        // 使用 splitByShift 拆分记录（使用 currentTime 作为结束时间）
        List<DeviceStateRecordDO> splitRecords = timeRangeRecordHandler.splitByShift(
                deviceId,
                orgFactoryId,
                startTs,
                currentTime,
                // RecordFactory: 创建拆分后的记录
                (deviceIdParam, factoryId, recordStartTs, recordEndTs) -> {
                    DeviceStateRecordDO newRecord = new DeviceStateRecordDO();
                    newRecord.setDeviceInfoId(deviceIdParam);
                    newRecord.setOrgFactoryId(factoryId);
                    newRecord.setStateCode(finalStateCode);
                    newRecord.setStartTs(recordStartTs);
                    newRecord.setEndTs(recordEndTs);
                    newRecord.setDurationS(recordEndTs - recordStartTs);
                    newRecord.setProperties(finalProperties);
                    newRecord.setIsComplete(true); // 拆分后的记录标记为完整
                    return newRecord;
                }
        );

        if (splitRecords.isEmpty()) {
            log.warn("[DeviceStateShiftSplitService] 拆分结果为空，跳过: deviceId={}, recordId={}", 
                    deviceId, record.getId());
            return;
        }

        // 处理最后一条记录为进行中状态（endTs=null）
        DeviceStateRecordDO lastRecord = splitRecords.get(splitRecords.size() - 1);
        lastRecord.setEndTs(null);
        lastRecord.setDurationS(null);
        lastRecord.setIsComplete(false);

        // 第一条记录：更新原记录
        DeviceStateRecordDO firstRecord = splitRecords.get(0);
        
        // 检查第一条记录的开始时间是否与原记录匹配
        if (!firstRecord.getStartTs().equals(record.getStartTs())) {
            log.warn("[DeviceStateShiftSplitService] 第一条记录的开始时间不匹配，使用插入方式: deviceId={}, " +
                            "recordStartTs={}, firstRecordStartTs={}", 
                    deviceId, record.getStartTs(), firstRecord.getStartTs());
            // 如果开始时间不匹配，说明原记录可能已经被部分处理，使用插入方式
            for (DeviceStateRecordDO r : splitRecords) {
                stateRecordRepository.insert(r);
                log.debug("[DeviceStateShiftSplitService] 插入拆分后的记录: deviceId={}, startTs={}, endTs={}, shiftDate={}, shiftCode={}", 
                        r.getDeviceInfoId(), r.getStartTs(), r.getEndTs(), r.getShiftDate(), r.getShiftCode());
            }
            log.info("[DeviceStateShiftSplitService] 拆分完成（插入方式）: deviceId={}, 原始记录1条, 拆分后{}条", 
                    deviceId, splitRecords.size());
            return;
        }

        // 更新原记录（第一条记录）
        record.setEndTs(firstRecord.getEndTs());
        record.setDurationS(firstRecord.getDurationS());
        record.setIsComplete(firstRecord.getIsComplete());
        record.setShiftDate(firstRecord.getShiftDate());
        record.setShiftCode(firstRecord.getShiftCode());
        record.setProperties(finalProperties);
        
        stateRecordRepository.update(record);
        log.debug("[DeviceStateShiftSplitService] 更新原记录: deviceId={}, recordId={}, startTs={}, endTs={}, " +
                        "durationS={}, shiftDate={}, shiftCode={}", 
                deviceId, record.getId(), record.getStartTs(), record.getEndTs(), 
                record.getDurationS(), record.getShiftDate(), record.getShiftCode());

        // 插入后续记录（从第二条开始）
        for (int i = 1; i < splitRecords.size(); i++) {
            DeviceStateRecordDO r = splitRecords.get(i);
            stateRecordRepository.insert(r);
            log.debug("[DeviceStateShiftSplitService] 插入拆分后的记录: deviceId={}, startTs={}, endTs={}, shiftDate={}, shiftCode={}", 
                    r.getDeviceInfoId(), r.getStartTs(), r.getEndTs(), r.getShiftDate(), r.getShiftCode());
        }

        log.info("[DeviceStateShiftSplitService] 拆分完成: deviceId={}, 原始记录1条（已更新）, 新增记录{}条", 
                deviceId, splitRecords.size() - 1);
    }
}
