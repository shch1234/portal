package com.weili.iot_portal.service.record;

import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.domain.record.TimeRangeRecord;
import com.weili.iot_portal.service.ingestion.handler.RecordHandlerUtils;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间范围记录处理服务
 * <p>
 * 统一处理时间范围记录的跨班次截断、过期数据、班次限制等逻辑
 * </p>
 * <p>
 * 使用场景：
 * - 更新旧记录时检查跨班次和过期
 * - 创建新记录时检查跨班次和超班次时长
 * - 按班次拆分长时段记录
 * </p>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeRangeRecordHandler {

    private final IShiftCalculationService shiftCalculationService;
    private final RecordHandlerUtils recordHandlerUtils;

    /**
     * 过期阈值（毫秒）
     * 如果记录的持续时间超过此阈值，将被标记为过期数据
     */
    @Value("${time.record.expiry.threshold.hours:48}")
    private long expiryThresholdHours;

    /**
     * 过期数据的结束时间标记
     * 使用特殊值-1标记过期数据，避免被正常查询扫描到
     */
    private static final long EXPIRED_END_TIMESTAMP = -1L;

    /**
     * 按班次截断记录的最大生成数量限制
     * 防止设备长时间离线导致生成过多记录
     * 注意：如果超过此限制，会创建溢出记录，但仍可能影响内存
     */
    @Value("${time.record.split.max.records:50}")
    private int maxSplitRecords;

    /**
     * 班次计算异常的特殊标记值
     * 用于标识班次计算异常，需要创建溢出记录
     */
    private static final Long SHIFT_CALCULATION_ERROR = Long.MIN_VALUE;

    /**
     * 获取过期阈值（毫秒）
     */
    private long getExpiryThresholdMs() {
        return expiryThresholdHours * 60 * 60 * 1000L;
    }

    /**
     * 如果班次信息缺失，根据开始时间补充
     * <p>
     * 委托给 RecordHandlerUtils 处理
     * </p>
     */
    public void fillShiftInfoIfMissing(TimeRangeRecord record, Long factoryId) {
        recordHandlerUtils.fillShiftInfoIfMissing(record, factoryId);
    }

    /**
     * 检查记录是否过期
     * <p>
     * 如果进行中的记录（endTs=null）与新事件的时间跨度超过阈值，则认为过期
     * </p>
     *
     * @param ongoingRecord     进行中的记录
     * @param newEventTimestamp 新事件时间戳（毫秒）
     * @return true 如果记录已过期
     */
    public boolean isExpired(TimeRangeRecord ongoingRecord, Long newEventTimestamp) {
        if (ongoingRecord == null || ongoingRecord.getStartTs() == null || newEventTimestamp == null) {
            return false;
        }

        // 如果记录已结束，不检查过期
        if (ongoingRecord.getEndTs() != null) {
            return false;
        }

        long duration = newEventTimestamp - ongoingRecord.getStartTs();
        return duration > getExpiryThresholdMs();
    }

    /**
     * 检查时间范围是否跨班次
     *
     * @param factoryId 工厂ID
     * @param deviceId  设备ID
     * @param startTs   开始时间戳（毫秒）
     * @param endTs     结束时间戳（毫秒）
     * @return true 如果跨班次
     */
    public boolean checkIfCrossesShift(Long factoryId, Long deviceId, Long startTs, Long endTs) {
        if (startTs == null || endTs == null) {
            return false;
        }
        return shiftCalculationService.checkIfCrossesShift(factoryId, deviceId, startTs, endTs);
    }

    /**
     * 按班次拆分记录
     * <p>
     * 如果记录跨班次，拆分为多条记录，每条记录属于一个班次
     * </p>
     *
     * @param deviceId      设备ID
     * @param factoryId     工厂ID
     * @param startTs       开始时间戳（毫秒）
     * @param endTs         结束时间戳（毫秒）
     * @param recordFactory 记录工厂方法，用于创建新记录实例
     * @param <T>           记录类型
     * @return 拆分后的记录列表
     */
    public <T extends TimeRangeRecord> List<T> splitByShift(
            Long deviceId, Long factoryId,
            Long startTs, Long endTs,
            RecordFactory<T> recordFactory) {

        List<T> records = new ArrayList<>();
        Long currentStartTs = startTs;
        Long previousStartTs = null;
        boolean infiniteLoopDetected = false;

        log.debug("[TimeRangeRecordHandler] 开始按班次截断: deviceId={}, startTs={}, endTs={}",
                deviceId, startTs, endTs);

        while (true) {
            // 检查是否应该继续拆分
            boolean shouldContinue = shouldContinueSplitting(currentStartTs, endTs, records.size(), previousStartTs, deviceId);
            
            // 如果检测到无限循环，标记并停止
            if (!shouldContinue && previousStartTs != null && currentStartTs != null && currentStartTs.equals(previousStartTs)) {
                infiniteLoopDetected = true;
                log.error("[TimeRangeRecordHandler] 检测到无限循环，停止截断: deviceId={}, currentStartTs={}, startTs={}, endTs={}, 已生成记录数={}",
                        deviceId, currentStartTs, startTs, endTs, records.size());
            }
            
            if (!shouldContinue) {
                // 如果因为记录数限制而停止，创建溢出记录
                if (records.size() >= maxSplitRecords && currentStartTs != null && currentStartTs < endTs) {
                    createOverflowRecord(deviceId, factoryId, currentStartTs, endTs, recordFactory, records);
                }
                // 如果只有1条记录但未覆盖全部时间范围，也创建溢出记录（防止班次计算异常导致的问题）
                else if (records.size() == 1 && currentStartTs != null && currentStartTs < endTs) {
                    T firstRecord = records.get(0);
                    Long firstRecordEndTs = firstRecord.getEndTs();
                    if (firstRecordEndTs != null && firstRecordEndTs < endTs) {
                        log.warn("[TimeRangeRecordHandler] 只有1条记录但未覆盖全部时间范围，创建溢出记录: " +
                                        "deviceId={}, firstRecordEndTs={}, endTs={}",
                                deviceId, firstRecordEndTs, endTs);
                        createOverflowRecord(deviceId, factoryId, firstRecordEndTs, endTs, recordFactory, records);
                    }
                }
                break;
            }

            try {
                previousStartTs = currentStartTs;

                // 1. 获取当前班次范围
                ShiftTimeRange currentShift = shiftCalculationService.calculateShiftRange(
                        factoryId, deviceId, currentStartTs);
                if (currentShift == null || currentShift.getEndTs() == null) {
                    log.warn("[TimeRangeRecordHandler] 无法计算班次范围，停止截断: deviceId={}, currentStartTs={}",
                            deviceId, currentStartTs);
                    break;
                }

                // 2. 计算记录结束时间
                Long recordEndTs = calculateRecordEndTime(currentShift, endTs, currentStartTs, deviceId);
                if (recordEndTs == null) {
                    // 零时长记录，尝试继续下一班次
                    currentStartTs = prepareNextShiftStart(currentShift.getEndTs(), endTs, factoryId, deviceId);
                    continue;
                }

                // 3. 创建记录
                T record = recordFactory.create(deviceId, factoryId, currentStartTs, recordEndTs);
                setShiftInfo(record, currentShift, factoryId);
                records.add(record);

                log.debug("[TimeRangeRecordHandler] 截断片段: deviceId={}, shiftDate={}, shiftCode={}, " +
                                "startTs={}, endTs={}, duration={}ms",
                        deviceId, record.getShiftDate(), record.getShiftCode(),
                        currentStartTs, recordEndTs, record.getDurationS());

                // 4. 准备下一班次
                Long nextStartTs = prepareNextShiftStart(recordEndTs, endTs, currentShift, factoryId, deviceId);
                if (nextStartTs == null) {
                    break; // 已完成截断
                }
                
                // 处理班次计算异常的情况
                if (nextStartTs.equals(SHIFT_CALCULATION_ERROR)) {
                    log.error("[TimeRangeRecordHandler] 检测到班次计算异常，创建溢出记录覆盖剩余时间范围: " +
                                    "deviceId={}, currentStartTs={}, recordEndTs={}, endTs={}",
                            deviceId, currentStartTs, recordEndTs, endTs);
                    // 创建溢出记录覆盖剩余时间范围，避免无限循环
                    if (recordEndTs < endTs) {
                        createOverflowRecord(deviceId, factoryId, recordEndTs, endTs, recordFactory, records);
                    }
                    // 标记最后一条记录为拆分不完整
                    if (!records.isEmpty()) {
                        T lastRecord = records.get(records.size() - 1);
                        Map<String, Object> properties = lastRecord.getProperties();
                        if (properties == null) {
                            properties = new HashMap<>();
                        }
                        properties.put("split_incomplete", true);
                        properties.put("split_incomplete_reason", "班次计算异常，使用溢出记录覆盖剩余时间范围");
                        properties.put("split_incomplete_record_end_ts", recordEndTs);
                        properties.put("split_incomplete_end_ts", endTs);
                        lastRecord.setProperties(properties);
                    }
                    break;
                }
                
                // 安全检查：防止下一班次开始时间等于当前开始时间（可能导致无限循环）
                if (nextStartTs.equals(currentStartTs)) {
                    log.error("[TimeRangeRecordHandler] 检测到班次计算异常：下一班次开始时间等于当前开始时间，停止截断: " +
                                    "deviceId={}, currentStartTs={}, recordEndTs={}, shiftEndTs={}, nextStartTs={}",
                            deviceId, currentStartTs, recordEndTs, currentShift.getEndTs(), nextStartTs);
                    // 这种情况说明班次计算有问题，直接结束截断，使用溢出记录机制
                    if (recordEndTs < endTs) {
                        createOverflowRecord(deviceId, factoryId, recordEndTs, endTs, recordFactory, records);
                    }
                    break;
                }
                
                currentStartTs = nextStartTs;
            } catch (Exception e) {
                log.error("[TimeRangeRecordHandler] 截断班次时发生异常，停止截断: deviceId={}, currentStartTs={}, error={}",
                        deviceId, currentStartTs, e.getMessage(), e);
                break;
            }
        }

        // 如果检测到无限循环且没有生成有效记录，返回空列表以避免重复处理
        if (infiniteLoopDetected && records.isEmpty()) {
            log.error("[TimeRangeRecordHandler] 无限循环导致无法拆分，返回空列表: deviceId={}, startTs={}, endTs={}",
                    deviceId, startTs, endTs);
            return records; // 返回空列表
        }

        // 如果检测到无限循环但已生成部分记录，标记最后一条记录
        if (infiniteLoopDetected && !records.isEmpty()) {
            T lastRecord = records.get(records.size() - 1);
            Map<String, Object> properties = lastRecord.getProperties();
            if (properties == null) {
                properties = new HashMap<>();
            }
            properties.put("split_incomplete", true);
            properties.put("split_incomplete_reason", "检测到无限循环，拆分未完成");
            properties.put("split_incomplete_start_ts", startTs);
            properties.put("split_incomplete_end_ts", endTs);
            lastRecord.setProperties(properties);
            log.warn("[TimeRangeRecordHandler] 无限循环导致拆分不完整，已标记最后一条记录: deviceId={}, recordCount={}",
                    deviceId, records.size());
        }

        log.info("[TimeRangeRecordHandler] 按班次截断完成: deviceId={}, 原始记录1条, 截断后{}条, 无限循环检测={}",
                deviceId, records.size(), infiniteLoopDetected);

        return records;
    }

    /**
     * 检查是否应该继续拆分
     */
    private boolean shouldContinueSplitting(Long currentStartTs, Long endTs, int recordCount,
                                           Long previousStartTs, Long deviceId) {
        if (currentStartTs == null || currentStartTs >= endTs) {
            return false;
        }

        // 安全检查：防止无限循环
        if (previousStartTs != null && currentStartTs.equals(previousStartTs)) {
            log.warn("[TimeRangeRecordHandler] 检测到可能的无限循环，停止截断: deviceId={}, currentStartTs={}",
                    deviceId, currentStartTs);
            return false;
        }

        // 安全检查：防止生成过多记录
        if (recordCount >= maxSplitRecords) {
            log.warn("[TimeRangeRecordHandler] 截断记录数量超过限制({})，停止截断以防止性能问题: deviceId={}, " +
                            "已生成记录数={}",
                    maxSplitRecords, deviceId, recordCount);
            return false;
        }

        return true;
    }

    /**
     * 计算记录结束时间
     *
     * @return 记录结束时间，如果为零时长则返回null
     */
    private Long calculateRecordEndTime(ShiftTimeRange shift, Long stateEndTs, Long currentStartTs, Long deviceId) {
        Long recordEndTs = Math.min(shift.getEndTs(), stateEndTs);

        // 跳过零时长或负时长的记录
        if (recordEndTs <= currentStartTs) {
            log.debug("[TimeRangeRecordHandler] 跳过零时长记录: deviceId={}, currentStartTs={}, recordEndTs={}",
                    deviceId, currentStartTs, recordEndTs);
            return null;
        }

        return recordEndTs;
    }

    /**
     * 准备下一班次的开始时间
     * 
     * @return 下一班次开始时间，如果已完成截断返回null，如果检测到班次计算异常返回SHIFT_CALCULATION_ERROR
     */
    private Long prepareNextShiftStart(Long recordEndTs, Long stateEndTs,
                                      ShiftTimeRange currentShift, Long orgFactoryId, Long deviceInfoId) {
        // 如果记录结束时间等于状态结束时间，已完成截断
        if (recordEndTs >= stateEndTs) {
            if (recordEndTs.equals(stateEndTs) && currentShift != null
                    && recordEndTs.equals(currentShift.getEndTs())) {
                log.debug("[TimeRangeRecordHandler] 状态结束时间等于班次边界时间，不创建下一班次记录: deviceId={}, recordEndTs={}",
                        deviceInfoId, recordEndTs);
            }
            return null;
        }

        // 如果记录结束时间等于班次结束时间，且状态还未结束，继续下一班次
        // 重要：使用半开区间 [start, end)，所以 recordEndTs（班次结束时间）属于下一班次
        if (currentShift != null && recordEndTs.equals(currentShift.getEndTs())) {
            // 直接使用 recordEndTs 查询下一班次（不使用 +1）
            // 因为班次使用半开区间，endTs 属于下一班次
            ShiftTimeRange nextShift = shiftCalculationService.calculateShiftRange(
                    orgFactoryId, deviceInfoId, recordEndTs);

            if (nextShift != null && nextShift.getStartTs() != null) {
                // 验证：下一班次开始时间应该等于当前班次结束时间（半开区间）
                if (!nextShift.getStartTs().equals(recordEndTs)) {
                    log.error("[TimeRangeRecordHandler] 班次计算异常：下一班次开始时间({}) != 当前班次结束时间({}), " +
                                    "可能是班次配置错误（相邻班次边界时间不同）: deviceId={}, recordEndTs={}, timeDiff={}ms",
                            nextShift.getStartTs(), recordEndTs, deviceInfoId, recordEndTs, 
                            nextShift.getStartTs() - recordEndTs);
                    // 返回特殊值，让上层知道需要创建溢出记录
                    return SHIFT_CALCULATION_ERROR;
                }
                
                // 额外检查：如果下一班次开始时间小于当前班次结束时间，说明配置错误
                if (nextShift.getStartTs() < recordEndTs) {
                    log.error("[TimeRangeRecordHandler] 班次计算异常：下一班次开始时间({}) < 当前班次结束时间({}), " +
                                    "班次配置错误，可能导致无限循环: deviceId={}, recordEndTs={}",
                            nextShift.getStartTs(), recordEndTs, deviceInfoId, recordEndTs);
                    return SHIFT_CALCULATION_ERROR;
                }
                
                log.debug("[TimeRangeRecordHandler] 切换到下一个班次: deviceId={}, 当前班次结束={}, 下一班次开始={}",
                        deviceInfoId, recordEndTs, nextShift.getStartTs());
                return nextShift.getStartTs();
            } else {
                log.warn("[TimeRangeRecordHandler] 无法获取下一个班次，停止截断: deviceId={}, recordEndTs={}",
                        deviceInfoId, recordEndTs);
                return null;
            }
        }

        return null;
    }

    /**
     * 重载方法：当只有recordEndTs时需要计算当前班次
     */
    private Long prepareNextShiftStart(Long recordEndTs, Long stateEndTs, Long orgFactoryId, Long deviceInfoId) {
        if (recordEndTs >= stateEndTs) {
            return null;
        }

        // 计算当前班次范围
        ShiftTimeRange currentShift = shiftCalculationService.calculateShiftRange(
                orgFactoryId, deviceInfoId, recordEndTs);
        return prepareNextShiftStart(recordEndTs, stateEndTs, currentShift, orgFactoryId, deviceInfoId);
    }

    /**
     * 创建溢出记录（当截断记录数量超过限制时使用）
     */
    private <T extends TimeRangeRecord> void createOverflowRecord(Long deviceId, Long factoryId,
                                                                  Long startTs, Long endTs,
                                                                  RecordFactory<T> recordFactory,
                                                                  List<T> records) {
        T overflowRecord = recordFactory.create(deviceId, factoryId, startTs, endTs);
        overflowRecord.setDurationS(endTs - startTs);

        // 设置班次信息（使用开始时间）
        if (startTs != null) {
            try {
                ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(factoryId, deviceId, startTs);
                overflowRecord.setShiftDate(shiftInfo.shiftDate());
                overflowRecord.setShiftCode(shiftInfo.shiftCode());
            } catch (Exception e) {
                log.warn("[TimeRangeRecordHandler] 计算溢出记录班次信息失败: deviceId={}, startTs={}, error={}",
                        deviceId, startTs, e.getMessage());
            }
        }

        // 添加溢出标记到属性
        Map<String, Object> overflowProperties = new HashMap<>();
        overflowProperties.put("overflow_merged", true);
        overflowProperties.put("overflow_reason", "记录数量超过限制(" + maxSplitRecords + ")");
        overflowProperties.put("original_start_ts", startTs);
        overflowProperties.put("original_end_ts", endTs);
        overflowRecord.setProperties(overflowProperties);

        records.add(overflowRecord);

        log.info("[TimeRangeRecordHandler] 创建溢出记录覆盖剩余时间范围: deviceId={}, startTs={}, endTs={}, duration={}ms",
                deviceId, startTs, endTs, overflowRecord.getDurationS());
    }

    /**
     * 设置班次信息到记录
     */
    private <T extends TimeRangeRecord> void setShiftInfo(T record, ShiftTimeRange shift, Long factoryId) {
        record.setShiftDate(shift.getShiftDate());
        record.setShiftCode(shift.getShiftCode());
        if (record.getStartTs() != null && record.getEndTs() != null) {
            record.setDurationS(record.getEndTs() - record.getStartTs());
        }
    }

    /**
     * 处理过期记录
     * <p>
     * 将超过阈值的时间标记为过期，避免数据爆炸和性能问题
     * </p>
     *
     * @param ongoingRecord     进行中的记录
     * @param newEventTimestamp 新事件时间戳（毫秒）
     * @param factoryId         工厂ID
     * @param recordUpdater     记录更新器，用于更新数据库
     * @param <T>               记录类型
     */
    public <T extends TimeRangeRecord> void handleExpiredRecord(
            T ongoingRecord,
            Long newEventTimestamp,
            Long factoryId,
            RecordUpdater<T> recordUpdater) {

        if (ongoingRecord == null || ongoingRecord.getStartTs() == null) {
            return;
        }

        log.info("[TimeRangeRecordHandler] 处理过期记录: deviceId={}, startTs={}, currentTs={}",
                ongoingRecord.getDeviceInfoId(), ongoingRecord.getStartTs(), newEventTimestamp);

        // 1. 标记为过期状态
        ongoingRecord.setEndTs(EXPIRED_END_TIMESTAMP);  // 特殊结束时间标记
        ongoingRecord.setDurationS(null);               // 不计算持续时间

        // 2. 添加过期标记到属性
        Map<String, Object> properties = ongoingRecord.getProperties();
        if (properties == null) {
            properties = new HashMap<>();
        }

        long originalDuration = newEventTimestamp - ongoingRecord.getStartTs();
        properties.put("expired", true);
        properties.put("expiry_reason", "记录持续时间超过" + expiryThresholdHours + "小时阈值");
        properties.put("expiry_threshold_hours", expiryThresholdHours);
        properties.put("original_duration_ms", originalDuration);
        properties.put("original_duration_hours", originalDuration / (60 * 60 * 1000L));
        properties.put("marked_at", newEventTimestamp);
        properties.put("marked_by", "TimeRangeRecordHandler");

        ongoingRecord.setProperties(properties);

        // 3. 更新班次信息（如果缺失）
        fillShiftInfoIfMissing(ongoingRecord, factoryId);

        // 4. 更新数据库记录
        if (recordUpdater != null) {
            recordUpdater.update(ongoingRecord);
        }

        log.info("[TimeRangeRecordHandler] 已标记过期记录: deviceId={}, endTs={}, duration=null",
                ongoingRecord.getDeviceInfoId(), EXPIRED_END_TIMESTAMP);
    }

    /**
     * 更新进行中的记录（统一入口）
     * <p>
     * 处理流程：
     * 1. 检查时间戳异常
     * 2. 检查是否过期
     * 3. 检查是否跨班次
     * 4. 拆分或更新
     * </p>
     *
     * @param ongoingRecord     进行中的记录
     * @param newEndTs          新的结束时间戳（毫秒）
     * @param factoryId         工厂ID
     * @param recordFactory     记录工厂，用于创建拆分后的记录
     * @param recordUpdater     记录更新器，用于更新/删除/插入数据库
     * @param <T>               记录类型
     * @return 是否创建了新记录（过期场景）
     */
    public <T extends TimeRangeRecord> boolean updateOngoingRecord(
            T ongoingRecord,
            Long newEndTs,
            Long factoryId,
            RecordFactory<T> recordFactory,
            RecordUpdater<T> recordUpdater) {

        if (ongoingRecord == null || newEndTs == null) {
            return false;
        }

        Long deviceId = ongoingRecord.getDeviceInfoId();
        Long oldStartTs = ongoingRecord.getStartTs();

        // 1. 检查时间戳异常
        if (oldStartTs != null && newEndTs < oldStartTs) {
            log.warn("[TimeRangeRecordHandler] 时间戳异常: deviceId={}, 事件时间={}, 记录开始时间={}",
                    deviceId, newEndTs, oldStartTs);
            // 时间戳异常处理由调用方负责
            return false;
        }

        // 2. 检查是否过期
        if (isExpired(ongoingRecord, newEndTs)) {
            handleExpiredRecord(ongoingRecord, newEndTs, factoryId, recordUpdater);
            return true; // 已标记为过期，需要创建新记录
        }

        // 3. 检查是否跨班次
        boolean crossesShift = checkIfCrossesShift(factoryId, deviceId, oldStartTs, newEndTs);

        if (crossesShift) {
            // 跨班次：删除旧记录，插入截断后的多条记录
            log.debug("[TimeRangeRecordHandler] 记录跨班次，进行截断: deviceId={}, startTs={}, endTs={}",
                    deviceId, oldStartTs, newEndTs);

            // 先创建截断后的记录（在删除前验证，避免数据丢失）
            List<T> splitRecords = splitByShift(deviceId, factoryId, oldStartTs, newEndTs, recordFactory);
            
            // 安全检查：如果拆分结果为空，不删除原记录，避免数据丢失
            if (splitRecords == null || splitRecords.isEmpty()) {
                log.error("[TimeRangeRecordHandler] 拆分结果为空，跳过删除和插入操作，避免数据丢失: deviceId={}, startTs={}, endTs={}",
                        deviceId, oldStartTs, newEndTs);
                return false; // 不创建新记录，保持原记录不变
            }

            // 删除旧记录（在确认有拆分结果后再删除）
            if (recordUpdater != null) {
                recordUpdater.delete(ongoingRecord);
            }

            // 插入截断后的记录
            if (recordUpdater != null) {
                try {
                    for (T record : splitRecords) {
                        recordUpdater.insert(record);
                    }
                    log.debug("[TimeRangeRecordHandler] 成功插入{}条拆分后的记录: deviceId={}, startTs={}, endTs={}",
                            splitRecords.size(), deviceId, oldStartTs, newEndTs);
                } catch (Exception e) {
                    // 插入失败：记录错误日志，异常会向上传播导致事务回滚，原记录会被恢复
                    log.error("[TimeRangeRecordHandler] 插入拆分后的记录失败，事务将回滚，原记录将被恢复: " +
                                    "deviceId={}, startTs={}, endTs={}, splitRecordsCount={}, error={}",
                            deviceId, oldStartTs, newEndTs, splitRecords.size(), e.getMessage(), e);
                    throw e; // 重新抛出异常，确保事务回滚
                }
            }
        } else {
            // 不跨班次：直接更新旧记录
            ongoingRecord.setEndTs(newEndTs);
            if (oldStartTs != null) {
                ongoingRecord.setDurationS(newEndTs - oldStartTs);
            }
            fillShiftInfoIfMissing(ongoingRecord, factoryId);

            if (recordUpdater != null) {
                recordUpdater.update(ongoingRecord);
            }

            log.debug("[TimeRangeRecordHandler] 更新记录: deviceId={}, endTs={}, durationS={}",
                    deviceId, ongoingRecord.getEndTs(), ongoingRecord.getDurationS());
        }

        return false; // 未创建新记录
    }

    /**
     * 记录工厂接口
     * 用于创建特定类型的记录实例
     */
    @FunctionalInterface
    public interface RecordFactory<T extends TimeRangeRecord> {
        /**
         * 创建记录实例
         *
         * @param deviceId  设备ID
         * @param factoryId 工厂ID
         * @param startTs   开始时间戳
         * @param endTs     结束时间戳
         * @return 记录实例
         */
        T create(Long deviceId, Long factoryId, Long startTs, Long endTs);
    }

    /**
     * 记录更新器接口
     * 用于更新/删除/插入数据库记录
     */
    public interface RecordUpdater<T extends TimeRangeRecord> {
        /**
         * 更新记录
         */
        void update(T record);

        /**
         * 删除记录
         */
        void delete(T record);

        /**
         * 插入记录
         */
        void insert(T record);
    }
}
