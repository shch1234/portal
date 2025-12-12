package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceProductionRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.ingestion.WebhookEventHandler;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceProductionEventFields;
import com.weili.iot_portal.service.shift.IShiftCalculationService;
import com.weili.iot_portal.service.shift.model.ShiftDateAndCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 设备产量事件处理器（开始/结束）
 * 事件类型：DEVICE_PRODUCTION
 * 字段：status=start/end（必填），ts（秒，必填），programName（选填），countSource（选填）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProductionEventHandler implements WebhookEventHandler {


    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final IShiftCalculationService shiftCalculationService;

    @Override
    public boolean supports(String eventType) {
        return DeviceProductionEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_PRODUCTION;
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

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceProductionEventFields.EVENT_SOURCE);
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        ShiftInfo shift = resolveShift(orgFactoryId, deviceInfoId, ts);

        if (DeviceProductionEventFields.STATUS_START.equalsIgnoreCase(status)) {
            handleStart(eventData, deviceInfoId, orgFactoryId, ts, shift);
        } else {
            handleEnd(eventData, deviceInfoId, orgFactoryId, ts, shift);
        }
    }

    private void handleStart(Map<String, Object> eventData, String deviceInfoId,
                             String orgFactoryId, Long ts, ShiftInfo shift) {
        // 若已有进行中记录，按需关闭；这里直接插入新记录
        DeviceProductionRecordDO record = new DeviceProductionRecordDO();
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStartTs(ts);
        record.setEndTs(null);
        record.setProgramName(toStr(eventData.get(DeviceProductionEventFields.PROGRAM_NAME)));
        record.setShiftDate(shift.shiftDate());
        record.setShiftCode(shift.shiftCode());
        record.setCountSource(toStr(eventData.get(DeviceProductionEventFields.COUNT_SOURCE)));
        deviceProductionRecordRepository.insert(record);
    }

    private void handleEnd(Map<String, Object> eventData, String deviceInfoId,
                           String orgFactoryId, Long ts, ShiftInfo shift) {
        Optional<DeviceProductionRecordDO> ongoingOpt = deviceProductionRecordRepository.findLatestOngoing(deviceInfoId);
        if (ongoingOpt.isEmpty()) {
            // 若没有进行中，补一条仅 end 的记录（起止相同）
            // 注意：device_production_record 表已删除 tenant_uuid 字段
            DeviceProductionRecordDO record = new DeviceProductionRecordDO();
            record.setDeviceInfoId(deviceInfoId);
            record.setOrgFactoryId(orgFactoryId);
            record.setStartTs(ts);
            record.setEndTs(ts);
            record.setDurationS(0);
            record.setProgramName(toStr(eventData.get(DeviceProductionEventFields.PROGRAM_NAME)));
            record.setShiftDate(shift.shiftDate());
            record.setShiftCode(shift.shiftCode());
            record.setCountSource(toStr(eventData.get(DeviceProductionEventFields.COUNT_SOURCE)));
            deviceProductionRecordRepository.insert(record);
            return;
        }

        DeviceProductionRecordDO ongoing = ongoingOpt.get();
        ongoing.setEndTs(ts);
        if (ongoing.getStartTs() != null) {
            ongoing.setDurationS((int) (ts - ongoing.getStartTs()));
        }
        if (ongoing.getShiftCode() == null) {
            ongoing.setShiftCode(shift.shiftCode());
        }
        if (ongoing.getShiftDate() == null) {
            ongoing.setShiftDate(shift.shiftDate());
        }
        deviceProductionRecordRepository.updateById(ongoing);
    }

    private ShiftInfo resolveShift(String factoryId, String deviceId, Long tsSeconds) {
        long tsMs = tsSeconds * DeviceProductionEventFields.SECONDS_TO_MILLIS;
        ShiftDateAndCode shiftInfo = shiftCalculationService.getShiftDateAndCode(factoryId, deviceId, tsMs);
        return new ShiftInfo(shiftInfo.shiftDate(), shiftInfo.shiftCode());
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private record ShiftInfo(LocalDate shiftDate, Integer shiftCode) {}
}


