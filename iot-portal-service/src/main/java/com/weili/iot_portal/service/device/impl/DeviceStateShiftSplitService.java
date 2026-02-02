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
                    ShouldSplitResult splitResult = shouldSplitWithErrorHandling(record, currentTime);
                    
                    if (splitResult.isError()) {
                        // 发生异常（通常是 Redis 连接异常），标记记录避免重复处理
                        markRecordAsSplitFailed(record, splitResult.getErrorReason(), currentTime);
                        errorCount++;
                        skipCount++;
                        log.debug("[DeviceStateShiftSplitService] 因异常标记记录为拆分失败，避免重复处理: deviceId={}, startTs={}, id={}, reason={}", 
                                record.getDeviceInfoId(), record.getStartTs(), record.getId(), splitResult.getErrorReason());
                    } else if (splitResult.shouldSplit()) {
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
     * 判断记录是否需要拆分（带异常处理结果）
     * 
     * @return ShouldSplitResult 包含是否需要拆分和异常信息
     */
    private ShouldSplitResult shouldSplitWithErrorHandling(DeviceStateRecordDO record, long currentTime) {
        Long startTs = record.getStartTs();
        if (startTs == null) {
            return ShouldSplitResult.noSplit();
        }

        // 检查记录是否已被标记为拆分失败，如果是则跳过
        Map<String, Object> properties = record.getProperties();
        if (properties != null && Boolean.TRUE.equals(properties.get("split_failed"))) {
            log.debug("[DeviceStateShiftSplitService] 记录已标记为拆分失败，跳过: deviceId={}, startTs={}, reason={}", 
                    record.getDeviceInfoId(), startTs, properties.get("split_failed_reason"));
            return ShouldSplitResult.noSplit();
        }

        // 关键优化：如果记录持续时间超过24小时，直接结束记录并标记异常，不再拆分
        // 因为拆分没有意义，且会产生大量记录导致内存问题
        long timeSpan = currentTime - startTs;
        long maxTimeSpan = 24L * 60 * 60 * 1000L; // 24小时
        
        if (timeSpan > maxTimeSpan) {
            long hours = timeSpan / (60 * 60 * 1000L);
            log.warn("[DeviceStateShiftSplitService] 记录持续时间过长（{}小时），直接结束记录并标记异常，不再拆分: " +
                            "deviceId={}, recordId={}, startTs={}, currentTime={}, timeSpan={}ms",
                    hours, record.getDeviceInfoId(), record.getId(), startTs, currentTime, timeSpan);
            
            // 直接结束记录并标记异常
            endRecordWithAbnormalMark(record, currentTime, 
                    String.format("记录持续时间过长（%d小时），可能存在异常，直接结束记录", hours));
            
            return ShouldSplitResult.noSplit(); // 不再拆分
        }

        try {
            // 获取开始时间所在的班次
            ShiftTimeRange startShift = shiftCalculationService.calculateShiftRange(
                    record.getOrgFactoryId(), record.getDeviceInfoId(), startTs);
            
            if (startShift == null || startShift.getEndTs() == null) {
                log.debug("[DeviceStateShiftSplitService] 无法计算班次范围，跳过: deviceId={}, startTs={}", 
                        record.getDeviceInfoId(), startTs);
                return ShouldSplitResult.noSplit();
            }

            // 检查当前时间是否超过了开始班次的结束时间
            // 如果超过，说明跨班次，需要拆分
            boolean crossesShift = currentTime > startShift.getEndTs();
            
            if (crossesShift) {
                log.debug("[DeviceStateShiftSplitService] 检测到跨班次: deviceId={}, startTs={}, shiftEndTs={}, currentTime={}", 
                        record.getDeviceInfoId(), startTs, startShift.getEndTs(), currentTime);
            }
            
            return ShouldSplitResult.of(crossesShift);
        } catch (Exception e) {
            // 检查是否是 Redis 连接相关的异常（应用关闭或 Redis 不可用）
            String errorMsg = e.getMessage();
            boolean isRedisConnectionError = errorMsg != null && (
                    errorMsg.contains("LettuceConnectionFactory has been STOPPED") 
                    || errorMsg.contains("event executor terminated")
                    || errorMsg.contains("Redis connection")
                    || errorMsg.contains("Connection refused")
                    || e.getClass().getSimpleName().contains("Redis")
            );
            
            if (isRedisConnectionError) {
                // Redis 连接异常（通常是应用关闭或 Redis 不可用），降低日志级别，避免循环打印
                log.debug("[DeviceStateShiftSplitService] 判断是否需要拆分时发生 Redis 连接异常（应用可能正在关闭）: deviceId={}, startTs={}, error={}", 
                        record.getDeviceInfoId(), startTs, e.getMessage());
                // 返回错误结果，让上层标记记录，避免重复处理
                return ShouldSplitResult.error("Redis连接异常: " + e.getMessage());
            } else {
                // 其他异常，保持 WARN 级别
                log.warn("[DeviceStateShiftSplitService] 判断是否需要拆分时发生异常: deviceId={}, startTs={}, error={}", 
                        record.getDeviceInfoId(), startTs, e.getMessage());
                // 其他异常也返回错误结果，避免重复处理
                return ShouldSplitResult.error("计算班次范围异常: " + e.getMessage());
            }
        }
    }

    /**
     * 直接结束记录并标记异常（用于长时间运行的记录）
     * 
     * @param record 记录
     * @param endTime 结束时间
     * @param reason 异常原因
     */
    private void endRecordWithAbnormalMark(DeviceStateRecordDO record, long endTime, String reason) {
        try {
            Long startTs = record.getStartTs();
            if (startTs == null) {
                log.warn("[DeviceStateShiftSplitService] 记录开始时间为空，无法结束记录: deviceId={}, recordId={}",
                        record.getDeviceInfoId(), record.getId());
                return;
            }
            
            Map<String, Object> properties = record.getProperties();
            if (properties == null) {
                properties = new HashMap<>();
            } else {
                properties = new HashMap<>(properties); // 创建副本，避免修改原始Map
            }
            
            // 标记为异常结束
            properties.put("abnormal_end", true);
            properties.put("abnormal_end_reason", reason);
            properties.put("abnormal_end_time", endTime);
            properties.put("abnormal_duration_hours", (endTime - startTs) / (60 * 60 * 1000L));
            
            // 设置结束时间和时长
            record.setEndTs(endTime);
            record.setDurationS(endTime - startTs); // durationS 以毫秒为单位存储
            record.setIsComplete(true); // 标记为完整
            record.setProperties(properties);
            
            // 更新记录
            stateRecordRepository.update(record);
            
            log.info("[DeviceStateShiftSplitService] 已结束长时间运行的记录并标记异常: deviceId={}, recordId={}, " +
                            "startTs={}, endTs={}, durationHours={}, reason={}",
                    record.getDeviceInfoId(), record.getId(), startTs, endTime,
                    (endTime - startTs) / (60 * 60 * 1000L), reason);
        } catch (Exception e) {
            // 标记失败不影响主流程，只记录日志
            log.warn("[DeviceStateShiftSplitService] 结束记录并标记异常时发生异常: deviceId={}, recordId={}, error={}", 
                    record.getDeviceInfoId(), record.getId(), e.getMessage());
        }
    }

    /**
     * 标记记录为拆分失败，避免重复处理
     */
    private void markRecordAsSplitFailed(DeviceStateRecordDO record, String reason, long currentTime) {
        try {
            Map<String, Object> properties = record.getProperties();
            if (properties == null) {
                properties = new HashMap<>();
            } else {
                properties = new HashMap<>(properties); // 创建副本，避免修改原始Map
            }
            
            properties.put("split_failed", true);
            properties.put("split_failed_reason", reason);
            properties.put("split_failed_time", currentTime);
            properties.put("split_failed_start_ts", record.getStartTs());
            
            record.setProperties(properties);
            stateRecordRepository.update(record);
        } catch (Exception e) {
            // 标记失败不影响主流程，只记录日志
            log.warn("[DeviceStateShiftSplitService] 标记记录为拆分失败时发生异常: deviceId={}, recordId={}, error={}", 
                    record.getDeviceInfoId(), record.getId(), e.getMessage());
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
            ShouldSplitResult splitResult = shouldSplitWithErrorHandling(latestRecord, currentTime);
            if (splitResult.isError()) {
                // 发生异常，标记记录并返回失败
                markRecordAsSplitFailed(latestRecord, splitResult.getErrorReason(), currentTime);
                log.debug("[DeviceStateShiftSplitService] 重新判断时发生异常，标记记录: deviceId={}, recordId={}, reason={}", 
                        deviceId, latestRecord.getId(), splitResult.getErrorReason());
                return false;
            }
            if (!splitResult.shouldSplit()) {
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
            log.error("[DeviceStateShiftSplitService] 拆分结果为空（可能是无限循环导致），标记记录为已处理: deviceId={}, recordId={}, startTs={}, currentTime={}", 
                    deviceId, record.getId(), startTs, currentTime);
            
            // 标记记录为已处理，避免重复处理
            markRecordAsSplitFailed(record, "拆分返回空结果（可能因无限循环）", currentTime);
            return;
        }

        // 检查拆分结果是否异常：只有1条记录但未覆盖全部时间范围
        if (splitRecords.size() == 1) {
            DeviceStateRecordDO singleRecord = splitRecords.get(0);
            Long singleRecordEndTs = singleRecord.getEndTs();
            // 如果记录有结束时间，且结束时间小于当前时间，说明未覆盖全部时间范围
            if (singleRecordEndTs != null && singleRecordEndTs < currentTime) {
                log.error("[DeviceStateShiftSplitService] 拆分结果异常：只有1条记录但未覆盖全部时间范围, " +
                                "标记记录为拆分失败: deviceId={}, recordId={}, startTs={}, recordEndTs={}, currentTime={}",
                        deviceId, record.getId(), startTs, singleRecordEndTs, currentTime);
                markRecordAsSplitFailed(record, 
                        String.format("拆分结果异常：只有1条记录但未覆盖全部时间范围（recordEndTs=%d, currentTime=%d）", 
                                singleRecordEndTs, currentTime), 
                        currentTime);
                return;
            }
        }

        // 检查最后一条记录是否标记为拆分不完整
        DeviceStateRecordDO lastRecord = splitRecords.get(splitRecords.size() - 1);
        Map<String, Object> lastRecordProperties = lastRecord.getProperties();
        boolean isIncomplete = lastRecordProperties != null && 
                Boolean.TRUE.equals(lastRecordProperties.get("split_incomplete"));
        
        if (isIncomplete) {
            log.error("[DeviceStateShiftSplitService] 检测到拆分不完整（无限循环），标记记录并跳过: deviceId={}, recordId={}, startTs={}", 
                    deviceId, record.getId(), startTs);
            
            // 标记原记录为拆分失败，避免重复处理
            Map<String, Object> errorProperties = new HashMap<>(properties);
            errorProperties.put("split_failed", true);
            errorProperties.put("split_failed_reason", "拆分过程中检测到无限循环");
            errorProperties.put("split_failed_time", currentTime);
            errorProperties.put("split_failed_start_ts", startTs);
            record.setProperties(errorProperties);
            stateRecordRepository.update(record);
            
            log.warn("[DeviceStateShiftSplitService] 已标记记录为拆分失败（无限循环），将跳过后续处理: deviceId={}, recordId={}", 
                    deviceId, record.getId());
            return;
        }
        
        // 处理最后一条记录为进行中状态（endTs=null）
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
        // 内存保护：如果拆分记录数过多，记录警告，避免一次性加载到内存
        int insertCount = splitRecords.size() - 1;
        if (insertCount > 50) {
            log.warn("[DeviceStateShiftSplitService] 拆分记录数较多（{}条），可能影响内存: deviceId={}, recordId={}",
                    insertCount, deviceId, record.getId());
        }
        
        for (int i = 1; i < splitRecords.size(); i++) {
            DeviceStateRecordDO r = splitRecords.get(i);
            stateRecordRepository.insert(r);
            log.debug("[DeviceStateShiftSplitService] 插入拆分后的记录: deviceId={}, startTs={}, endTs={}, shiftDate={}, shiftCode={}", 
                    r.getDeviceInfoId(), r.getStartTs(), r.getEndTs(), r.getShiftDate(), r.getShiftCode());
        }

        log.info("[DeviceStateShiftSplitService] 拆分完成: deviceId={}, 原始记录1条（已更新）, 新增记录{}条", 
                deviceId, splitRecords.size() - 1);
    }

    /**
     * 判断是否需要拆分的结果封装类
     */
    private static class ShouldSplitResult {
        private final boolean shouldSplit;
        private final boolean isError;
        private final String errorReason;

        private ShouldSplitResult(boolean shouldSplit, boolean isError, String errorReason) {
            this.shouldSplit = shouldSplit;
            this.isError = isError;
            this.errorReason = errorReason;
        }

        public static ShouldSplitResult of(boolean shouldSplit) {
            return new ShouldSplitResult(shouldSplit, false, null);
        }

        public static ShouldSplitResult noSplit() {
            return new ShouldSplitResult(false, false, null);
        }

        public static ShouldSplitResult error(String reason) {
            return new ShouldSplitResult(false, true, reason);
        }

        public boolean shouldSplit() {
            return shouldSplit;
        }

        public boolean isError() {
            return isError;
        }

        public String getErrorReason() {
            return errorReason;
        }
    }
}
