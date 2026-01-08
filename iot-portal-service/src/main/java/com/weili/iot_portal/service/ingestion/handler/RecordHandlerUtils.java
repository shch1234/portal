package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.ShiftTimeRange;
import com.weili.iot_portal.domain.record.TimeRangeRecord;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
