package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.domain.ingestion.DeviceIdentity;
import com.weili.iot_portal.domain.ingestion.ShiftDateAndCode;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.WebhookProcessingStrategy;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceProductionEventFields;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 设备产量事件处理器（开始/结束）
 * <p>
 * 事件类型：DEVICE_PRODUCTION
 * 字段：status=start/end（必填），ts（秒，必填），programName（选填），countSource（选填）
 * </p>
 * <p>
 * <b>产量记录的特殊性：</b>
 * <ul>
 *   <li><b>跨班次不拆分</b>：产量记录如果跨班次时不拆分，统计时它是属于结束班次的产量</li>
 *   <li><b>班次归属</b>：使用结束时间的班次信息（统计时属于结束班次）</li>
 *   <li><b>过期处理</b>：如果记录超过24小时未结束，直接结束记录，不创建新记录（避免产量错误）</li>
 * </ul>
 * </p>
 * <p>
 * <b>与状态记录/刀具记录的区别：</b>
 * <ul>
 *   <li>状态记录/刀具记录：跨班次需要拆分，每条记录属于一个班次</li>
 *   <li>产量记录：跨班次不拆分，属于结束班次</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProductionEventHandler implements WebhookEventHandler {


    private final WebhookHandlerUtils webhookHandlerUtils;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final IShiftCalculationService shiftCalculationService;
    private final com.weili.iot_portal.service.record.TimeRangeRecordHandler timeRangeRecordHandler;
    private final RecordHandlerUtils recordHandlerUtils;

    @Override
    public boolean supports(String eventType) {
        return DeviceProductionEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_PRODUCTION;
    }

    @Override
    public WebhookProcessingStrategy getProcessingStrategy() {
        // 业务持久化处理：需要写数据库，经过收件箱，支持重试
        return WebhookProcessingStrategy.BUSINESS_PERSISTENT;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }
        String status = toStr(eventData.get(DeviceProductionEventFields.STATUS));
        if (!DeviceProductionEventFields.isValidStatus(status)) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_PRODUCTION_STATUS_INVALID);
        }

        Long ts = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp() : null);
        if (ts == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_PRODUCTION_TIMESTAMP_EMPTY);
        }

        DeviceIdentity identity = 
                webhookHandlerUtils.resolveDeviceIdentity(request);
        Long deviceInfoId = identity.deviceInfoId();
        Long orgFactoryId = identity.orgFactoryId();

        ShiftInfo shift = resolveShift(orgFactoryId, deviceInfoId, ts);

        if (DeviceProductionEventFields.STATUS_START.equalsIgnoreCase(status)) {
            handleStart(eventData, deviceInfoId, orgFactoryId, ts);
        } else {
            handleEnd(eventData, deviceInfoId, orgFactoryId, ts, shift);
        }
    }

    /**
     * 处理产量开始事件
     * <p>
     * 创建新的产量记录，使用开始时间的班次信息（记录还未结束）
     * </p>
     */
    private void handleStart(Map<String, Object> eventData, Long deviceInfoId,
                             Long orgFactoryId, Long ts) {
        // 若已有进行中记录，按需关闭；这里直接插入新记录
        DeviceProductionRecordDO record = new DeviceProductionRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        // 时间戳转换为毫秒（通用服务使用毫秒）
        long tsMs = ts * DeviceProductionEventFields.SECONDS_TO_MILLIS;
        record.setStartTs(tsMs);
        record.setEndTs(null);
        record.setProgramName(toStr(eventData.get(DeviceProductionEventFields.PROGRAM_NAME)));
        record.setCountSource(toStr(eventData.get(DeviceProductionEventFields.COUNT_SOURCE)));
        // 使用通用工具类设置班次信息（使用开始时间的班次，因为记录还未结束）
        recordHandlerUtils.fillShiftInfoIfMissing(record, orgFactoryId);
        deviceProductionRecordRepository.insert(record);
    }

    /**
     * 处理产量结束事件
     * <p>
     * 特殊处理逻辑：
     * <ul>
     *   <li>跨班次不拆分：直接更新记录，不进行跨班次拆分</li>
     *   <li>使用结束时间班次：产量记录统计时属于结束班次</li>
     *   <li>过期处理：如果超过24小时未结束，直接结束记录，不创建新记录（避免产量错误）</li>
     * </ul>
     * </p>
     */
    private void handleEnd(Map<String, Object> eventData, Long deviceInfoId,
                           Long orgFactoryId, Long ts, ShiftInfo shift) {
        // 时间戳转换为毫秒（通用服务使用毫秒）
        long tsMs = ts * DeviceProductionEventFields.SECONDS_TO_MILLIS;
        
        Optional<DeviceProductionRecordDO> ongoingOpt = deviceProductionRecordRepository.findLatestOngoing(deviceInfoId);
        if (ongoingOpt.isEmpty()) {
            // 若没有进行中，补一条仅 end 的记录（起止相同）
            DeviceProductionRecordDO record = new DeviceProductionRecordDO();
            record.setDeviceInfoId(deviceInfoId);
            record.setOrgFactoryId(orgFactoryId);
            record.setStartTs(tsMs);
            record.setEndTs(tsMs);
            record.setDurationS(0L);
            record.setProgramName(toStr(eventData.get(DeviceProductionEventFields.PROGRAM_NAME)));
            record.setCountSource(toStr(eventData.get(DeviceProductionEventFields.COUNT_SOURCE)));
            // 使用结束时间的班次信息（产量记录属于结束班次）
            record.setShiftDate(shift.shiftDate());
            record.setShiftCode(shift.shiftCode());
            deviceProductionRecordRepository.insert(record);
            return;
        }

        DeviceProductionRecordDO ongoing = ongoingOpt.get();
        
        // 1. 检查是否过期（24小时未结束）
        // 注意：产量记录如果过期，直接结束记录，不创建新记录（避免产量错误）
        if (timeRangeRecordHandler.isExpired(ongoing, tsMs)) {
            log.warn("[DeviceProductionEventHandler] 产量记录超过24小时未结束，直接结束: deviceId={}, startTs={}, endTs={}",
                    deviceInfoId, ongoing.getStartTs(), tsMs);
            // 直接结束记录，不创建新记录（避免产量错误）
            ongoing.setEndTs(tsMs);
            if (ongoing.getStartTs() != null) {
                ongoing.setDurationS(tsMs - ongoing.getStartTs());
            }
            // 使用结束时间的班次信息（产量记录属于结束班次）
            ongoing.setShiftDate(shift.shiftDate());
            ongoing.setShiftCode(shift.shiftCode());
            deviceProductionRecordRepository.updateById(ongoing);
            return;
        }

        // 2. 正常更新记录（不拆分，产量记录跨班次时不拆分）
        ongoing.setEndTs(tsMs);
        if (ongoing.getStartTs() != null) {
            ongoing.setDurationS(tsMs - ongoing.getStartTs());
        }
        // 使用结束时间的班次信息（产量记录属于结束班次）
        // 注意：产量记录统计时属于结束班次，所以使用结束时间的班次信息
        ongoing.setShiftDate(shift.shiftDate());
        ongoing.setShiftCode(shift.shiftCode());
        deviceProductionRecordRepository.updateById(ongoing);
    }

    /**
     * 解析班次信息
     * <p>
     * 注意：产量记录使用结束时间的班次信息（统计时属于结束班次）
     * </p>
     *
     * @param factoryId 工厂ID
     * @param deviceId 设备ID
     * @param tsSeconds 时间戳（秒）
     * @return 班次信息
     */
    private ShiftInfo resolveShift(Long factoryId, Long deviceId, Long tsSeconds) {
        long tsMs = tsSeconds * DeviceProductionEventFields.SECONDS_TO_MILLIS;
        ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(factoryId, deviceId, tsMs);
        return new ShiftInfo(shiftInfo.shiftDate(), shiftInfo.shiftCode());
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private record ShiftInfo(LocalDate shiftDate, Integer shiftCode) {}
}


