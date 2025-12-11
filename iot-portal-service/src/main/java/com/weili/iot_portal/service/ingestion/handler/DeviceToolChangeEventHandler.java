package com.weili.iot_portal.service.ingestion.handler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.device.DeviceToolRecordDO;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.dal.repository.device.DeviceToolRecordRepository;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceLockService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceToolEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 刀具换刀事件处理器
 * <p>
 * 处理 DEVICE_TOOL_CHANGE 事件，逻辑类似状态事件：比对上一个刀具号，关闭旧记录，插入新记录
 * </p>
 * <p>
 * 事件数据要求（eventData）：
 * - previousToolNo: 上一个刀号
 * - currentToolNo: 当前刀号（必填）
 * - toolHolderNumber/toolMagazineNo（可选）
 * - toolId/toolType/compensationSnapshot（可选）
 * - timestamp 或 dataTimestamp（毫秒）
 * </p>
 * <p>
 * 字段定义请参考：{@link DeviceToolEventFields}
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceToolChangeEventHandler implements WebhookEventHandler {

    private final DeviceToolRecordRepository deviceToolRecordRepository;
    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceLockService deviceLockService;

    @Override
    public boolean supports(String eventType) {
        return DeviceToolEventFields.EVENT_TYPE_CHANGE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_TOOL_CHANGE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        var eventData = request.getEventData();
        if (eventData == null) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        String previousToolNo = getString(eventData, DeviceToolEventFields.PREVIOUS_TOOL_NO);
        String currentToolNo = getString(eventData, DeviceToolEventFields.CURRENT_TOOL_NO);
        if (StringUtils.isBlank(currentToolNo)) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_TOOL_NUMBER_EMPTY);
        }

        Long eventTimestampMs = request.getDataTimestamp() != null ? request.getDataTimestamp() : request.getTimestamp();
        if (eventTimestampMs == null) {
            eventTimestampMs = System.currentTimeMillis();
        }
        long eventTsSeconds = eventTimestampMs / DeviceToolEventFields.MILLIS_TO_SECONDS;

        // 解析设备
        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceToolEventFields.EVENT_SOURCE_CHANGE);
        String deviceInfoId = identity.getDeviceId();

        // 分布式锁，避免并发换刀
        if (!deviceLockService.tryLockToolChange(deviceInfoId, DeviceToolEventFields.LOCK_TIMEOUT_SECONDS_TOOL_CHANGE)) {
            log.warn("获取刀具锁失败，可能正在并发处理: deviceInfoId={}", deviceInfoId);
            throw new IotPortalException(IotPortalErrorCode.EVENT_TOOL_CHANGE_PROCESSING);
        }

        try {
            // 1) 关闭旧刀记录（如果存在）
            DeviceToolRecordDO latest = deviceToolRecordRepository.findLatestOngoing(deviceInfoId);
            if (latest != null) {
                if (StringUtils.isNotBlank(previousToolNo) && !previousToolNo.equalsIgnoreCase(latest.getToolNo())) {
                    log.warn("刀号不匹配: DB={}, eventPrevious={}, deviceInfoId={}", latest.getToolNo(), previousToolNo, deviceInfoId);
                }
                latest.setEndTs(eventTsSeconds);
                if (latest.getStartTs() != null) {
                    latest.setDurationS((int) (eventTsSeconds - latest.getStartTs()));
                }
                deviceToolRecordRepository.updateById(latest);
            }

            // 2) 插入新刀记录
            DeviceToolRecordDO newRecord = new DeviceToolRecordDO();
            newRecord.setId(IdWorker.getIdStr());
            // tenant_uuid 字段已删除
            newRecord.setDeviceInfoId(deviceInfoId);
            newRecord.setToolNo(currentToolNo);
            newRecord.setToolMagazineNo(getString(eventData, DeviceToolEventFields.TOOL_HOLDER_NUMBER));
            if (StringUtils.isBlank(newRecord.getToolMagazineNo())) {
                newRecord.setToolMagazineNo(getString(eventData, DeviceToolEventFields.TOOL_MAGAZINE_NO));
            }
            newRecord.setToolId(getString(eventData, DeviceToolEventFields.TOOL_ID));
            newRecord.setToolType(getString(eventData, DeviceToolEventFields.TOOL_TYPE));
            newRecord.setStartTs(eventTsSeconds);
            newRecord.setEndTs(null);
            newRecord.setDurationS(null);
            deviceToolRecordRepository.insert(newRecord);

            log.info("刀具变更完成: deviceId={}, prev={}, curr={}, ts={}", deviceInfoId, previousToolNo, currentToolNo, eventTsSeconds);
        } finally {
            deviceLockService.unlockToolChange(deviceInfoId);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}

