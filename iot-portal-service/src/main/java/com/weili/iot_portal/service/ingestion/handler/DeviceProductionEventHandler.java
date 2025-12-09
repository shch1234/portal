package com.weili.iot_portal.service.ingestion.handler;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.dal.dataobject.devicemng.DeviceProductionRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.devicemng.DeviceProductionRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.support.ShiftConfigurationService;
import com.weili.iot_portal.service.support.ShiftTimeRange;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private static final String EVENT_TYPE = "DEVICE_PRODUCTION";

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceProductionRecordRepository deviceProductionRecordRepository;
    private final ShiftConfigurationService shiftConfigurationService;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "事件数据不能为空");
        }
        String status = toStr(eventData.get("status"));
        if (!("start".equalsIgnoreCase(status) || "end".equalsIgnoreCase(status))) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "status 必须为 start/end");
        }

        Long ts = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp() : null);
        if (ts == null) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "ts 不能为空");
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getTenantId(), request.getDeviceCode(),
                        request.getDeviceId(), "DeviceProductionEvent");
        String tenantId = request.getTenantId();
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        ShiftInfo shift = resolveShift(tenantId, orgFactoryId, deviceInfoId, ts);

        if ("start".equalsIgnoreCase(status)) {
            handleStart(eventData, tenantId, deviceInfoId, orgFactoryId, ts, shift);
        } else {
            handleEnd(eventData, tenantId, deviceInfoId, orgFactoryId, ts, shift);
        }
    }

    private void handleStart(Map<String, Object> eventData, String tenantId, String deviceInfoId,
                             String orgFactoryId, Long ts, ShiftInfo shift) {
        // 若已有进行中记录，按需关闭；这里直接插入新记录
        DeviceProductionRecordDO record = new DeviceProductionRecordDO();
        record.setTenantUuid(tenantId);
        record.setDeviceInfoId(deviceInfoId);
        record.setOrgFactoryId(orgFactoryId);
        record.setStartTs(ts);
        record.setEndTs(null);
        record.setProgramName(toStr(eventData.get("programName")));
        record.setShiftDate(shift.shiftDate());
        record.setShiftCode(shift.shiftCode());
        record.setCountSource(toStr(eventData.get("countSource")));
        deviceProductionRecordRepository.insert(record);
    }

    private void handleEnd(Map<String, Object> eventData, String tenantId, String deviceInfoId,
                           String orgFactoryId, Long ts, ShiftInfo shift) {
        Optional<DeviceProductionRecordDO> ongoingOpt = deviceProductionRecordRepository.findLatestOngoing(tenantId, deviceInfoId);
        if (ongoingOpt.isEmpty()) {
            // 若没有进行中，补一条仅 end 的记录（起止相同）
            DeviceProductionRecordDO record = new DeviceProductionRecordDO();
            record.setTenantUuid(tenantId);
            record.setDeviceInfoId(deviceInfoId);
            record.setOrgFactoryId(orgFactoryId);
            record.setStartTs(ts);
            record.setEndTs(ts);
            record.setDurationS(0);
            record.setProgramName(toStr(eventData.get("programName")));
            record.setShiftDate(shift.shiftDate());
            record.setShiftCode(shift.shiftCode());
            record.setCountSource(toStr(eventData.get("countSource")));
            deviceProductionRecordRepository.insert(record);
            return;
        }

        DeviceProductionRecordDO ongoing = ongoingOpt.get();
        ongoing.setEndTs(ts);
        if (ongoing.getStartTs() != null) {
            ongoing.setDurationS((int) (ts - ongoing.getStartTs()));
        }
        if (StringUtils.isBlank(ongoing.getShiftCode())) {
            ongoing.setShiftCode(shift.shiftCode());
        }
        if (ongoing.getShiftDate() == null) {
            ongoing.setShiftDate(shift.shiftDate());
        }
        deviceProductionRecordRepository.updateById(ongoing);
    }

    private ShiftInfo resolveShift(String tenantId, String factoryId, String deviceId, Long tsSeconds) {
        long tsMs = tsSeconds * 1000L;
        ShiftTimeRange range = shiftConfigurationService.calculateShiftRange(tenantId, factoryId, deviceId, tsMs);
        LocalDate shiftDate = Instant.ofEpochMilli(range.getStartTs())
                .atZone(ZoneOffset.systemDefault())
                .toLocalDate();
        return new ShiftInfo(shiftDate, range.getShiftCode());
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    private record ShiftInfo(LocalDate shiftDate, String shiftCode) {}
}


