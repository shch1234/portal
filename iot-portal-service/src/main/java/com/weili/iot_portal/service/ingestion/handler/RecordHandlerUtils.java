package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.domain.record.TimeRangeRecord;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 记录处理工具类
 * <p>
 * 提供记录处理的公共工具方法，供各个 Handler 使用
 * </p>
 * <p>
 * 与 {@link WebhookHandlerUtils} 类似，但专注于记录相关的工具方法
 * </p>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordHandlerUtils {

    private final IShiftCalculationService shiftCalculationService;

    /**
     * 如果班次信息缺失，根据开始时间补充
     * <p>
     * 参考 {@link com.weili.iot_portal.service.ingestion.handler.DeviceStateEventHandler#fillShiftInfoIfMissing}
     * 的统一实现
     * </p>
     *
     * @param record    记录对象（实现 TimeRangeRecord 接口）
     * @param factoryId 工厂ID
     */
    public void fillShiftInfoIfMissing(TimeRangeRecord record, Long factoryId) {
        if (record == null || record.getStartTs() == null) {
            return;
        }

        // 如果班次信息已存在，不需要补充
        if (record.getShiftDate() != null && record.getShiftCode() != null) {
            return;
        }

        try {
            ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(
                    factoryId, record.getDeviceInfoId(), record.getStartTs());

            if (record.getShiftDate() == null) {
                record.setShiftDate(shiftInfo.shiftDate());
            }
            if (record.getShiftCode() == null) {
                record.setShiftCode(shiftInfo.shiftCode());
            }
        } catch (Exception e) {
            log.warn("[RecordHandlerUtils] 补充班次信息失败: deviceInfoId={}, startTs={}, error={}",
                    record.getDeviceInfoId(), record.getStartTs(), e.getMessage());
        }
    }

    /**
     * 验证班次边界有效性
     * <p>
     * 检查班次时间范围的有效性，包括：
     * 1. 开始时间必须小于结束时间
     * 2. 班次时长必须大于0且不超过24小时
     * 3. 时间范围必须在合理范围内
     * </p>
     * <p>
     * 从 {@link DeviceStateEventHandler#validateShiftBoundary} 提取的通用方法
     * </p>
     *
     * @param shiftRange 班次时间范围
     * @return true 如果班次边界有效
     */
    public boolean validateShiftBoundary(ShiftTimeRange shiftRange) {
        if (shiftRange == null || shiftRange.getStartTs() == null || shiftRange.getEndTs() == null) {
            return false;
        }

        long startTs = shiftRange.getStartTs();
        long endTs = shiftRange.getEndTs();
        long durationMs = endTs - startTs;

        // 基本验证：结束时间必须大于开始时间
        if (endTs <= startTs) {
            log.warn("[RecordHandlerUtils] 班次结束时间小于等于开始时间: startTs={}, endTs={}", startTs, endTs);
            return false;
        }

        // 时长验证：班次时长必须在合理范围内 (1分钟到24小时)
        long minShiftDuration = 60 * 1000; // 1分钟
        long maxShiftDuration = 24 * 60 * 60 * 1000; // 24小时

        if (durationMs < minShiftDuration || durationMs > maxShiftDuration) {
            log.warn("[RecordHandlerUtils] 班次时长超出合理范围: duration={}ms, min={}ms, max={}ms",
                    durationMs, minShiftDuration, maxShiftDuration);
            return false;
        }

        // 时间范围验证：不能是过去的班次或太远的未来
        long currentTime = System.currentTimeMillis();
        long maxPastTime = 365 * 24 * 60 * 60 * 1000L; // 1年
        long maxFutureTime = 7 * 24 * 60 * 60 * 1000L; // 1周

        if (startTs < currentTime - maxPastTime || startTs > currentTime + maxFutureTime) {
            log.warn("[RecordHandlerUtils] 班次开始时间超出合理时间范围: startTs={}, currentTime={}", startTs, currentTime);
            return false;
        }

        return true;
    }

    /**
     * 检查时间戳异常（事件时间 < 记录开始时间）
     * <p>
     * 从多个 Handler 中提取的公共逻辑
     * </p>
     *
     * @param record        记录对象
     * @param eventTimestamp 事件时间戳
     * @return true 如果时间戳异常（事件时间早于记录开始时间）
     */
    public boolean isTimestampAnomaly(TimeRangeRecord record, Long eventTimestamp) {
        if (record == null || record.getStartTs() == null || eventTimestamp == null) {
            return false;
        }
        return eventTimestamp < record.getStartTs();
    }

    /**
     * 结束记录（统一处理 endTs, durationS, 班次信息）
     * <p>
     * 从多个 Handler 中提取的公共逻辑，统一处理记录结束时的字段设置
     * </p>
     *
     * @param record                记录对象
     * @param endTimestamp          结束时间戳
     * @param orgFactoryId          工厂ID
     * @param precomputedShiftInfo  预计算的班次信息（可选，如果为null则查询数据库）
     * @param isComplete            是否完整（可选，某些记录类型需要，如 DeviceStateRecordDO）
     */
    public void endRecord(TimeRangeRecord record, Long endTimestamp, Long orgFactoryId,
                         ShiftDateAndCode precomputedShiftInfo, Boolean isComplete) {
        if (record == null || endTimestamp == null) {
            return;
        }

        // 设置结束时间
        record.setEndTs(endTimestamp);

        // 计算持续时间
        if (record.getStartTs() != null) {
            long duration = endTimestamp - record.getStartTs();
            record.setDurationS(duration < 0 ? 0 : duration);
        }

        // 设置是否完整（如果记录类型支持）
        if (isComplete != null && record instanceof com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO) {
            ((com.weili.iot_portal.dal.dataobject.device.DeviceStateRecordDO) record).setIsComplete(isComplete);
        }

        // 设置班次信息（优先使用预计算的，失败则查询数据库）
        if (precomputedShiftInfo != null) {
            if (record.getShiftDate() == null) {
                record.setShiftDate(precomputedShiftInfo.shiftDate());
            }
            if (record.getShiftCode() == null) {
                record.setShiftCode(precomputedShiftInfo.shiftCode());
            }
        }

        // 如果预计算失败或未提供，回退到数据库查询
        if (record.getShiftDate() == null || record.getShiftCode() == null) {
            fillShiftInfoIfMissing(record, orgFactoryId);
        }
    }

    /**
     * 预计算班次信息（锁外计算，减少锁内数据库查询）
     * <p>
     * 优化：在获取锁之前提前计算班次信息，减少锁持有时间
     * </p>
     *
     * @param record                已有记录（可选，如果为null则只计算新记录的班次信息）
     * @param newRecordTimestamp    新记录的时间戳
     * @param orgFactoryId          工厂ID
     * @param deviceInfoId          设备ID
     * @return 预计算的班次信息（record的班次信息，新记录的班次信息）
     */
    public PrecomputedShiftInfo precomputeShiftInfo(TimeRangeRecord record, Long newRecordTimestamp,
                                                    Long orgFactoryId, Long deviceInfoId) {
        ShiftDateAndCode recordShiftInfo = null;
        ShiftDateAndCode newRecordShiftInfo = null;
        ShiftTimeRange recordShiftRange = null;
        ShiftTimeRange newRecordShiftRange = null;

        // 计算已有记录的班次信息（仅在缺失时计算）
        if (record != null && record.getStartTs() != null
                && (record.getShiftDate() == null || record.getShiftCode() == null)) {
            try {
                recordShiftInfo = shiftCalculationService.getShiftDateAndCode(
                        orgFactoryId, deviceInfoId, record.getStartTs());
            } catch (Exception e) {
                log.warn("[RecordHandlerUtils] 提前计算已有记录班次信息失败: deviceInfoId={}, startTs={}, error={}",
                        deviceInfoId, record.getStartTs(), e.getMessage());
            }
        }
        
        // 总是计算已有记录的班次时间范围（用于跨班次检查，避免锁内查询）
        // 注意：即使记录已有班次信息，仍需要班次范围来判断是否跨班次
        if (record != null && record.getStartTs() != null) {
            try {
                recordShiftRange = shiftCalculationService.calculateShiftRange(
                        orgFactoryId, deviceInfoId, record.getStartTs());
            } catch (Exception e) {
                log.warn("[RecordHandlerUtils] 提前计算已有记录班次范围失败: deviceInfoId={}, startTs={}, error={}",
                        deviceInfoId, record.getStartTs(), e.getMessage());
            }
        }

        // 计算新记录的班次信息（基于事件时间戳）
        if (newRecordTimestamp != null) {
            try {
                newRecordShiftInfo = shiftCalculationService.getShiftDateAndCode(
                        orgFactoryId, deviceInfoId, newRecordTimestamp);
                // 同时计算班次时间范围（用于跨班次检查，避免锁内查询）
                newRecordShiftRange = shiftCalculationService.calculateShiftRange(
                        orgFactoryId, deviceInfoId, newRecordTimestamp);
            } catch (Exception e) {
                log.warn("[RecordHandlerUtils] 提前计算新记录班次信息失败: deviceInfoId={}, eventTimestamp={}, error={}",
                        deviceInfoId, newRecordTimestamp, e.getMessage());
            }
        }

        return new PrecomputedShiftInfo(recordShiftInfo, newRecordShiftInfo, recordShiftRange, newRecordShiftRange);
    }

    /**
     * 统一批量插入（统一日志、错误处理、空列表检查）
     * <p>
     * 从多个 Handler 中提取的公共逻辑，统一处理批量插入的日志和错误处理
     * </p>
     *
     * @param insertMethod 插入方法（返回插入的记录数）
     * @param records      记录列表
     * @param operation    操作描述（用于日志）
     * @param handlerName  Handler名称（用于日志）
     */
    public <T> void insertBatch(Supplier<Integer> insertMethod, List<T> records,
                                String operation, String handlerName) {
        if (records == null || records.isEmpty()) {
            log.debug("[{}] {}: 记录列表为空，跳过插入", handlerName, operation);
            return;
        }

        try {
            int count = insertMethod.get();
            log.debug("[{}] {}: 批量插入成功，记录数={}", handlerName, operation, count);
        } catch (Exception e) {
            log.error("[{}] {}: 批量插入失败，记录数={}", handlerName, operation, records.size(), e);
            throw e;
        }
    }

    /**
     * 预计算的班次信息
     */
    public record PrecomputedShiftInfo(
            ShiftDateAndCode recordShiftInfo,      // 已有记录的班次信息
            ShiftDateAndCode newRecordShiftInfo,   // 新记录的班次信息
            ShiftTimeRange recordShiftRange,       // 已有记录的班次时间范围（用于跨班次检查）
            ShiftTimeRange newRecordShiftRange     // 新记录的班次时间范围（用于跨班次检查）
    ) {
        public PrecomputedShiftInfo(ShiftDateAndCode recordShiftInfo, ShiftDateAndCode newRecordShiftInfo) {
            this(recordShiftInfo, newRecordShiftInfo, null, null);
        }
    }
}
