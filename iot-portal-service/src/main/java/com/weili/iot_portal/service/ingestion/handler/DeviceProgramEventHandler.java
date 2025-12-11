package com.weili.iot_portal.service.ingestion.handler;

import com.weili.iot_portal.common.exception.IotPortalErrorCode;
import com.weili.iot_portal.common.exception.IotPortalException;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.DeviceIdentityCacheService;
import com.weili.iot_portal.service.cache.DeviceProgramCacheService;
import com.weili.iot_portal.service.ingestion.handler.fields.DeviceProgramEventFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备程序实时事件处理器
 * 事件类型：DEVICE_PROGRAM
 * 事件数据：programName、programPath、gCode、mCode（可选）以及其他字段透传
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceProgramEventHandler implements WebhookEventHandler {


    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final DeviceProgramCacheService deviceProgramCacheService;

    @Override
    public boolean supports(String eventType) {
        return DeviceProgramEventFields.EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return WebhookHandlerOrder.DEVICE_PROGRAM;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new IotPortalException(IotPortalErrorCode.EVENT_DATA_EMPTY);
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), DeviceProgramEventFields.EVENT_SOURCE);
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis());

        Map<String, String> programData = extractProgramFields(eventData);
        if (programData.isEmpty()) {
            log.warn("DEVICE_PROGRAM 事件未包含程序字段，跳过: deviceInfoId={}", deviceInfoId);
            return;
        }
        deviceProgramCacheService.saveProgram(orgFactoryId, deviceInfoId, programData,
                eventTimestamp, DeviceProgramEventFields.SOURCE_TB, request.getMessageId());
    }

    private Map<String, String> extractProgramFields(Map<String, Object> eventData) {
        Map<String, String> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            if (DeviceProgramEventFields.isProgramNameField(key)) {
                map.put(DeviceProgramEventFields.PROGRAM_NAME, String.valueOf(v));
            } else if (DeviceProgramEventFields.isProgramPathField(key)) {
                map.put(DeviceProgramEventFields.PROGRAM_PATH, String.valueOf(v));
            } else if (DeviceProgramEventFields.isGCodeField(key)) {
                map.put(DeviceProgramEventFields.G_CODE, String.valueOf(v));
            } else if (DeviceProgramEventFields.isMCodeField(key)) {
                map.put(DeviceProgramEventFields.M_CODE, String.valueOf(v));
            } else if (DeviceProgramEventFields.isProgramRelatedField(key)) {
                map.put(key, String.valueOf(v));
            }
        });
        return map;
    }

}


