package com.weili.iot_portal.service.device.impl;

import com.weili.iot_portal.common.enums.DeviceStateEnum;
import com.weili.iot_portal.dal.dataobject.device.DeviceAlarmHistoryDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceInfoDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.repository.device.DeviceAlarmHistoryRepository;
import com.weili.iot_portal.dal.repository.device.DeviceInfoRepository;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceStateRecordRepository;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.BatchProcessResult;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.cache.DeviceStateCacheService;
import com.weili.iot_portal.service.cache.ResourceLimiter;
import com.weili.iot_portal.service.device.IDeviceOfflineReconciliationService;
import com.weili.iot_portal.service.ingestion.handler.RecordHandlerUtils;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceAlarmEventFields;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备离线纠正服务实现
 * <p>
 * 基于心跳状态扫描设备：
 * - heartbeat == "1"：认为在线，不做处理；
 * - heartbeat == "0"：认为离线，结束进行中的状态，并插入 UNKNOWN(255) 状态。
 * <p>
 * 注意：
 * - 离线判定完全基于 Redis 心跳键的TTL；
 * - 统计时包含 isComplete = false 的异常片段；
 * - 对于已经处于 UNKNOWN(255) 且进行中的记录，不再重复插入离线片段。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOfflineReconciliationService implements IDeviceOfflineReconciliationService {

    // ==================== 常量定义 ====================
    
    private static final String OFFLINE_REASON = "HEARTBEAT_EXPIRED";
    private static final String PROP_OFFLINE = "offline";
    private static final String PROP_OFFLINE_REASON = "offline_reason";
    private static final String PROP_OFFLINE_DETECT_TS = "offline_detect_ts";
    
    // 设备信息不完整警告的去重间隔（5分钟）
    private static final long INCOMPLETE_DEVICE_WARN_INTERVAL_MILLIS = 5 * 60 * 1000;
    
    // 记录最近警告的设备ID和时间戳（用于去重）
    private final Map<Long, Long> incompleteDeviceLastWarnTime = new java.util.concurrent.ConcurrentHashMap<>();

    // ==================== 依赖注入 ====================
    
    private final DeviceInfoRepository deviceInfoRepository;
    private final DeviceStateRecordRepository stateRecordRepository;
    private final DeviceToolRecordRepository toolRecordRepository;
    private final DeviceProductionRecordRepository productionRecordRepository;
    private final DeviceAlarmHistoryRepository alarmHistoryRepository;
    private final DeviceStateCacheService deviceStateCacheService;
    private final RecordHandlerUtils recordHandlerUtils;
    private final IShiftCalculationService shiftCalculationService;
    private final DeviceLockService deviceLockService;
    private final ResourceLimiter resourceLimiter;

    // ==================== 主处理方法 ====================

    @Override
    public BatchProcessResult processAllDevices(long currentTimeMillis, int batchSize, long timeoutMillis) {
        long start = System.currentTimeMillis();

        // 1. 查询所有被监控设备
        List<DeviceInfoDO> devices = deviceInfoRepository.findMonitoredDevices(null);
        if (devices == null || devices.isEmpty()) {
            log.debug("[DeviceOfflineReconcile] 无被监控设备，跳过本次任务");
            return BatchProcessResult.completed(0, 0, 0);
        }

        // 2. 批量查询心跳状态（提升效率）
        Map<Long, String> heartbeatMap = batchGetHeartbeatStatus(devices);

        int success = 0;
        int skip = 0;
        int error = 0;

        // 3. 按批次处理设备
        int processed = 0;
        for (DeviceInfoDO device : devices) {
            if (processed >= batchSize) {
                break;
            }

            // 超时保护
            if (System.currentTimeMillis() - start > timeoutMillis) {
                log.warn("[DeviceOfflineReconcile] 任务执行超时，已处理设备数: {}", processed);
                break;
            }

            try {
                // 从批量查询结果中获取心跳状态
                String heartbeat = heartbeatMap.getOrDefault(device.getId(), "0");
                boolean handled = processSingleDevice(device, heartbeat, currentTimeMillis);
                if (handled) {
                    success++;
                } else {
                    skip++;
                }
            } catch (Exception e) {
                error++;
                log.error("[DeviceOfflineReconcile] 处理设备离线纠正失败, deviceId={}, factoryId={}",
                        device.getId(), device.getOrgFactoryId(), e);
            }

            processed++;
        }

        return BatchProcessResult.completed(success, skip, error);
    }

    /**
     * 批量查询设备心跳状态（提升效率）
     */
    private Map<Long, String> batchGetHeartbeatStatus(List<DeviceInfoDO> devices) {
        Map<Long, String> result = new HashMap<>();
        
        // 按工厂ID分组，批量查询
        Map<Long, List<Long>> factoryDeviceMap = new HashMap<>();
        for (DeviceInfoDO device : devices) {
            if (device.getOrgFactoryId() != null && device.getId() != null) {
                factoryDeviceMap.computeIfAbsent(device.getOrgFactoryId(), k -> new java.util.ArrayList<>())
                        .add(device.getId());
            }
        }

        // 批量查询每个工厂的设备心跳状态
        for (Map.Entry<Long, List<Long>> entry : factoryDeviceMap.entrySet()) {
            Long factoryId = entry.getKey();
            List<Long> deviceIds = entry.getValue();
            Map<Long, String> factoryHeartbeatMap = deviceStateCacheService.batchGetHeartbeatStatus(factoryId, deviceIds);
            result.putAll(factoryHeartbeatMap);
        }

        return result;
    }

    /**
     * 处理单个设备的离线纠正逻辑
     * <p>
     * 优化：
     * 1. 使用分布式锁避免并发处理同一设备
     * 2. 使用限流器避免MySQL行锁竞争
     * 3. 每个更新操作使用独立事务，快速提交
     * </p>
     *
     * @param device            设备信息
     * @param heartbeat         心跳状态（"1"=在线，"0"=离线）
     * @param currentTimeMillis 当前时间（毫秒）
     * @return 是否执行了离线纠正（true=有处理，false=无处理）
     */
    private boolean processSingleDevice(DeviceInfoDO device, String heartbeat, long currentTimeMillis) {
        Long factoryId = device.getOrgFactoryId();
        Long deviceId = device.getId();

        if (factoryId == null || deviceId == null) {
            // 去重：同一设备在5分钟内只警告一次
            long now = System.currentTimeMillis();
            Long lastWarnTime = incompleteDeviceLastWarnTime.get(deviceId);
            boolean shouldWarn = lastWarnTime == null || (now - lastWarnTime) >= INCOMPLETE_DEVICE_WARN_INTERVAL_MILLIS;
            
            if (shouldWarn) {
                incompleteDeviceLastWarnTime.put(deviceId, now);
                log.warn("[DeviceOfflineReconcile] 设备信息不完整，跳过: deviceId={}, factoryId={}",
                        deviceId, factoryId);
            } else {
                log.debug("[DeviceOfflineReconcile] 设备信息不完整，跳过（已警告，5分钟内不再重复）: deviceId={}, factoryId={}",
                        deviceId, factoryId);
            }
            return false;
        }

        // 1. 检查心跳状态
        if (!"0".equals(heartbeat)) {
            // 在线设备不做处理
            return false;
        }

        // 2. 获取分布式锁（避免多个任务同时处理同一设备）
        // 优化：使用10秒超时，因为每个独立事务最多10秒，正常情况下总耗时应在1-2秒内
        // 即使有10条报警，也应在5-10秒内完成。如果超过10秒，说明数据库有问题，应该快速失败
        if (!deviceLockService.tryLockState(deviceId, 10)) {
            log.debug("[DeviceOfflineReconcile] 获取设备锁失败，跳过: deviceId={}", deviceId);
            return false;
        }

        try {
            // 3. 获取MySQL更新许可（限流，避免行锁竞争）
            if (!resourceLimiter.tryAcquireDeviceUpdate(deviceId)) {
                log.warn("[DeviceOfflineReconcile] 获取设备更新许可失败，跳过: deviceId={}", deviceId);
                return false;
            }

            try {
                // 4. 查询最新状态记录（只查询一次，避免重复查询）
                Optional<DeviceStateRecordDO> latestOpt = stateRecordRepository.findLatestState(deviceId);
                
                // 5. 检查是否需要处理状态记录（传入查询结果，避免重复查询）
                if (!shouldProcessStateRecord(latestOpt)) {
                    return false;
                }

                // 6. 处理状态记录（每个操作使用独立事务）
                if (latestOpt.isPresent()) {
                    DeviceStateRecordDO latest = latestOpt.get();
                    // 结束当前进行中的正常状态（传入查询结果，避免重复查询）
                    // 注意：endOngoingStateAsOffline 内部会重新查询以确保记录是最新的
                    boolean stateEnded = endOngoingStateAsOffline(latest, factoryId, currentTimeMillis);
                    if (stateEnded) {
                        // 只有成功结束状态后，才插入离线状态记录
                        insertOfflineStateRecord(latest, factoryId, currentTimeMillis);
                    } else {
                        log.debug("[DeviceOfflineReconcile] 结束状态记录失败，跳过插入离线状态记录: deviceId={}",
                                deviceId);
                    }
                }

                // 7. 处理其他类型的记录（并行处理，互不依赖）
                endOngoingToolAsOffline(deviceId, factoryId, currentTimeMillis);
                endOngoingProductionAsOffline(deviceId, factoryId, currentTimeMillis);
                endOngoingAlarmsAsOffline(deviceId, factoryId, currentTimeMillis);

                return true;
            } finally {
                // 释放MySQL更新许可
                resourceLimiter.releaseDeviceUpdate(deviceId);
            }
        } finally {
            // 释放分布式锁
            deviceLockService.unlockState(deviceId);
        }
    }

    /**
     * 判断是否需要处理状态记录
     * <p>
     * 优化：接收已查询的记录，避免重复查询
     * </p>
     * 
     * @param latestOpt 已查询的最新状态记录（可为空）
     * @return true 如果需要处理，false 如果不需要处理
     */
    private boolean shouldProcessStateRecord(Optional<DeviceStateRecordDO> latestOpt) {
        if (latestOpt.isEmpty()) {
            log.debug("[DeviceOfflineReconcile] 设备无状态记录，跳过离线纠正");
            return false;
        }

        DeviceStateRecordDO latest = latestOpt.get();
        
        // 如果最新记录已经结束，无需处理
        if (latest.getEndTs() != null) {
            return false;
        }

        // 如果最新记录已经是 UNKNOWN(255) 且进行中，说明离线片段已存在
        DeviceStateEnum latestStateEnum = DeviceStateEnum.fromCode(latest.getStateCode());
        if (latestStateEnum == DeviceStateEnum.UNKNOWN) {
            log.debug("[DeviceOfflineReconcile] 设备已存在进行中的UNKNOWN状态，跳过: deviceId={}, recordId={}",
                    latest.getDeviceInfoId(), latest.getId());
            return false;
        }

        return true;
    }

    // ==================== 状态记录处理 ====================

    /**
     * 将当前进行中的状态标记为"因离线而异常结束"
     * <p>
     * 优化：
     * 1. 使用独立事务（REQUIRES_NEW），快速提交，减少锁持有时间
     * 2. 锁超时后不抛出异常，避免拖垮程序，记录警告并返回false
     * 3. 锁内重新查询记录，确保是最新的（可能已被实时事件处理）
     * </p>
     * 
     * @return true 如果成功更新，false 如果锁超时或记录已被处理
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10, rollbackFor = Exception.class)
    private boolean endOngoingStateAsOffline(DeviceStateRecordDO record, Long factoryId, long offlineDetectTime) {
        try {
            // 锁内重新查询记录，确保是最新的（可能已被实时事件处理）
            // 注意：虽然外层已经查询过，但锁内必须重新查询，因为可能已被实时事件处理
            Optional<DeviceStateRecordDO> latestOpt = stateRecordRepository.findLatestState(record.getDeviceInfoId());
            
            if (latestOpt.isEmpty()) {
                log.debug("[DeviceOfflineReconcile] 记录不存在，可能已被删除: deviceId={}, oldRecordId={}",
                        record.getDeviceInfoId(), record.getId());
                return false;
            }
            
            DeviceStateRecordDO latestRecord = latestOpt.get();
            
            // 检查记录ID是否匹配（如果不匹配，说明记录已被实时事件处理）
            if (!latestRecord.getId().equals(record.getId())) {
                log.debug("[DeviceOfflineReconcile] 记录已被实时事件处理，跳过: deviceId={}, oldId={}, newId={}",
                        record.getDeviceInfoId(), record.getId(), latestRecord.getId());
                return false;
            }
            
            // 再次检查记录是否仍然需要处理（可能已被实时事件处理）
            if (latestRecord.getEndTs() != null) {
                log.debug("[DeviceOfflineReconcile] 记录已结束，不需要处理: deviceId={}, recordId={}",
                        record.getDeviceInfoId(), latestRecord.getId());
                return false;
            }
            
            // 计算并设置结束时间和持续时间
            TimeRangeResult timeRange = calculateEndTimeAndDuration(latestRecord.getStartTs(), offlineDetectTime);
            latestRecord.setEndTs(timeRange.endTs);
            latestRecord.setDurationS(timeRange.duration);
            latestRecord.setIsComplete(false);

            // 添加离线异常信息到 properties
            addOfflineProperties(latestRecord.getProperties(), offlineDetectTime, latestRecord::setProperties);

            // 补全班次信息并更新
            recordHandlerUtils.fillShiftInfoIfMissing(latestRecord, factoryId);
            stateRecordRepository.update(latestRecord);

            DeviceStateEnum stateEnum = DeviceStateEnum.fromCode(latestRecord.getStateCode());
            log.info("[DeviceOfflineReconcile] 标记进行中状态为离线结束: deviceId={}, recordId={}, state={}, startTs={}, endTs={}",
                    latestRecord.getDeviceInfoId(), latestRecord.getId(), stateEnum.name(), 
                    latestRecord.getStartTs(), timeRange.endTs);
            return true;
        } catch (CannotAcquireLockException e) {
            // 数据库锁超时：可能与其他事务冲突，记录警告但不抛出异常
            log.warn("[DeviceOfflineReconcile] 更新状态记录锁超时，跳过: deviceId={}, recordId={}, error={}",
                    record.getDeviceInfoId(), record.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            // 其他异常，记录错误但不抛出，避免拖垮程序
            log.error("[DeviceOfflineReconcile] 更新状态记录失败，跳过: deviceId={}, recordId={}, error={}",
                    record.getDeviceInfoId(), record.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * 插入一条新的 UNKNOWN(255) 进行中状态记录，表示离线期间
     * <p>
     * 优化：使用独立事务，快速提交
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10, rollbackFor = Exception.class)
    private void insertOfflineStateRecord(DeviceStateRecordDO previousState, Long factoryId, long offlineStartTime) {
        DeviceStateRecordDO offlineRecord = new DeviceStateRecordDO();
        offlineRecord.setDeviceInfoId(previousState.getDeviceInfoId());
        offlineRecord.setOrgFactoryId(factoryId);
        offlineRecord.setStateCode(DeviceStateEnum.UNKNOWN.getCode());
        offlineRecord.setStartTs(offlineStartTime);
        offlineRecord.setEndTs(null);
        offlineRecord.setDurationS(null);
        offlineRecord.setIsComplete(false);

        // 创建离线状态记录的 properties
        Map<String, Object> properties = new HashMap<>();
        DeviceStateEnum previousStateEnum = DeviceStateEnum.fromCode(previousState.getStateCode());
        properties.put(PROP_OFFLINE, true);
        properties.put(PROP_OFFLINE_REASON, OFFLINE_REASON);
        properties.put("from_state", previousStateEnum.name());
        properties.put("from_state_code", previousState.getStateCode());
        properties.put("offline_start_ts", offlineStartTime);
        offlineRecord.setProperties(properties);

        // 补全班次信息并插入
        recordHandlerUtils.fillShiftInfoIfMissing(offlineRecord, factoryId);
        stateRecordRepository.insert(offlineRecord);

        log.info("[DeviceOfflineReconcile] 插入离线状态记录: deviceId={}, state=UNKNOWN(255), startTs={}",
                offlineRecord.getDeviceInfoId(), offlineStartTime);
    }

    // ==================== 刀具记录处理 ====================

    /**
     * 结束正在使用的刀具记录（因离线而异常结束）
     * <p>
     * 优化：使用独立事务，快速提交
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10, rollbackFor = Exception.class)
    private void endOngoingToolAsOffline(Long deviceId, Long factoryId, long offlineDetectTime) {
        DeviceToolRecordDO ongoingTool = toolRecordRepository.findLatestOngoing(deviceId);
        if (ongoingTool == null) {
            return;
        }

        // 计算并设置结束时间和持续时间
        TimeRangeResult timeRange = calculateEndTimeAndDuration(ongoingTool.getStartTs(), offlineDetectTime);
        ongoingTool.setEndTs(timeRange.endTs);
        ongoingTool.setDurationS(timeRange.duration);

        // 添加离线异常信息到补偿快照
        addOfflineToCompensationSnapshot(ongoingTool.getCompensationSnapshot(), offlineDetectTime, 
                ongoingTool::setCompensationSnapshot);

        // 补全班次信息并更新
        recordHandlerUtils.fillShiftInfoIfMissing(ongoingTool, factoryId);
        toolRecordRepository.updateById(ongoingTool);

        log.info("[DeviceOfflineReconcile] 标记进行中刀具为离线结束: deviceId={}, toolRecordId={}, toolNo={}, toolId={}, startTs={}, endTs={}",
                deviceId, ongoingTool.getId(), ongoingTool.getToolNo(), ongoingTool.getToolId(), 
                ongoingTool.getStartTs(), timeRange.endTs);
    }

    // ==================== 产量记录处理 ====================

    /**
     * 结束正在进行的产量记录（因离线而异常结束）
     * <p>
     * 优化：使用独立事务，快速提交
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10, rollbackFor = Exception.class)
    private void endOngoingProductionAsOffline(Long deviceId, Long factoryId, long offlineDetectTime) {
        Optional<DeviceProductionRecordDO> ongoingOpt = productionRecordRepository.findLatestOngoing(deviceId);
        if (ongoingOpt.isEmpty()) {
            return;
        }

        DeviceProductionRecordDO ongoing = ongoingOpt.get();

        // 计算并设置结束时间和持续时间
        TimeRangeResult timeRange = calculateEndTimeAndDuration(ongoing.getStartTs(), offlineDetectTime);
        ongoing.setEndTs(timeRange.endTs);
        ongoing.setDurationS(timeRange.duration);

        // 添加离线异常信息到 properties
        addOfflineProperties(ongoing.getProperties(), offlineDetectTime, ongoing::setProperties);

        // 补全班次信息并更新
        recordHandlerUtils.fillShiftInfoIfMissing(ongoing, factoryId);
        productionRecordRepository.updateById(ongoing);

        log.info("[DeviceOfflineReconcile] 标记进行中产量为离线结束: deviceId={}, productionRecordId={}, workpieceNo={}, startTs={}, endTs={}",
                deviceId, ongoing.getId(), ongoing.getWorkpieceNo(), ongoing.getStartTs(), timeRange.endTs);
    }

    // ==================== 报警记录处理 ====================

    /**
     * 结束正在进行的报警记录（因离线而异常结束）
     * <p>
     * 优化：使用独立事务，快速提交
     * </p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10, rollbackFor = Exception.class)
    private void endOngoingAlarmsAsOffline(Long deviceId, Long factoryId, long offlineDetectTime) {
        List<DeviceAlarmHistoryDO> activeAlarms = alarmHistoryRepository.findActiveByDevice(factoryId, deviceId);
        if (activeAlarms == null || activeAlarms.isEmpty()) {
            return;
        }

        // 计算结束班次信息（所有报警使用相同的结束时间，所以班次信息也相同）
        ShiftDateAndCode endShiftInfo = calculateShiftInfo(factoryId, deviceId, offlineDetectTime);

        // 处理每条正在进行的报警
        for (DeviceAlarmHistoryDO alarm : activeAlarms) {
            // 计算并设置结束时间和持续时间
            TimeRangeResult timeRange = calculateEndTimeAndDuration(alarm.getStartTs(), offlineDetectTime);
            alarm.setEndTs(timeRange.endTs);
            alarm.setDurationS((int) timeRange.duration);
            alarm.setIsActive(DeviceAlarmEventFields.ACTIVE_STATUS_DISABLED);

            // 设置结束班次信息
            if (endShiftInfo != null) {
                alarm.setEndShiftDate(endShiftInfo.shiftDate());
                alarm.setEndShiftCode(endShiftInfo.shiftCode());
            }

            // 添加离线异常信息到 properties
            addOfflineProperties(alarm.getProperties(), offlineDetectTime, alarm::setProperties);

            // 补充开始班次信息（如果缺失）
            fillStartShiftInfoIfMissing(alarm, factoryId, deviceId);

            // 更新数据库
            alarmHistoryRepository.updateById(alarm);

            log.info("[DeviceOfflineReconcile] 标记进行中报警为离线结束: deviceId={}, alarmId={}, alarmCode={}, startTs={}, endTs={}",
                    deviceId, alarm.getId(), alarm.getAlarmCode(), alarm.getStartTs(), timeRange.endTs);
        }
    }

    /**
     * 补充报警记录的开始班次信息（如果缺失）
     */
    private void fillStartShiftInfoIfMissing(DeviceAlarmHistoryDO alarm, Long factoryId, Long deviceId) {
        if (alarm.getStartTs() == null) {
            return;
        }

        if (alarm.getStartShiftDate() != null && alarm.getStartShiftCode() != null) {
            return; // 已存在，无需补充
        }

        try {
            ShiftDateAndCode startShiftInfo = shiftCalculationService.getShiftDateAndCode(
                    factoryId, deviceId, alarm.getStartTs());
            if (alarm.getStartShiftDate() == null) {
                alarm.setStartShiftDate(startShiftInfo.shiftDate());
            }
            if (alarm.getStartShiftCode() == null) {
                alarm.setStartShiftCode(startShiftInfo.shiftCode());
            }
        } catch (Exception e) {
            log.warn("[DeviceOfflineReconcile] 补充开始班次信息失败: deviceId={}, alarmId={}, startTs={}, error={}",
                    deviceId, alarm.getId(), alarm.getStartTs(), e.getMessage());
        }
    }

    // ==================== 公共工具方法 ====================

    /**
     * 计算结束时间和持续时间
     * 
     * @param startTs 开始时间（毫秒，可为null）
     * @param offlineDetectTime 离线检测时间（毫秒）
     * @return 时间范围结果（结束时间和持续时间）
     */
    private TimeRangeResult calculateEndTimeAndDuration(Long startTs, long offlineDetectTime) {
        long endTs = offlineDetectTime;
        if (startTs != null && startTs > endTs) {
            // 防御性处理：避免负时长
            endTs = startTs;
        }
        long duration = (startTs != null) ? Math.max(0, endTs - startTs) : 0;
        return new TimeRangeResult(endTs, duration);
    }

    /**
     * 添加离线异常信息到 properties 字段
     * 
     * @param existingProperties 现有的 properties（可为null）
     * @param offlineDetectTime 离线检测时间（毫秒）
     * @param setter properties 设置器
     */
    private void addOfflineProperties(Map<String, Object> existingProperties, long offlineDetectTime,
                                     java.util.function.Consumer<Map<String, Object>> setter) {
        Map<String, Object> properties = existingProperties != null ? existingProperties : new HashMap<>();
        properties.put(PROP_OFFLINE, true);
        properties.put(PROP_OFFLINE_REASON, OFFLINE_REASON);
        properties.put(PROP_OFFLINE_DETECT_TS, offlineDetectTime);
        setter.accept(properties);
    }

    /**
     * 添加离线异常信息到补偿快照字段
     * 
     * @param existingSnapshot 现有的补偿快照（可为null）
     * @param offlineDetectTime 离线检测时间（毫秒）
     * @param setter 补偿快照设置器
     */
    private void addOfflineToCompensationSnapshot(Map<String, Object> existingSnapshot, long offlineDetectTime,
                                                  java.util.function.Consumer<Map<String, Object>> setter) {
        Map<String, Object> snapshot = existingSnapshot != null ? existingSnapshot : new HashMap<>();
        snapshot.put(PROP_OFFLINE, true);
        snapshot.put(PROP_OFFLINE_REASON, OFFLINE_REASON);
        snapshot.put(PROP_OFFLINE_DETECT_TS, offlineDetectTime);
        setter.accept(snapshot);
    }

    /**
     * 计算班次信息（带异常处理）
     * 
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param timestamp 时间戳（毫秒）
     * @return 班次信息，如果计算失败返回null
     */
    private ShiftDateAndCode calculateShiftInfo(Long factoryId, Long deviceId, long timestamp) {
        try {
            return shiftCalculationService.getShiftDateAndCode(factoryId, deviceId, timestamp);
        } catch (Exception e) {
            log.warn("[DeviceOfflineReconcile] 计算班次信息失败: deviceId={}, timestamp={}, error={}",
                    deviceId, timestamp, e.getMessage());
            return null;
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 时间范围计算结果
     */
    private record TimeRangeResult(long endTs, long duration) {}
}
