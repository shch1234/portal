package com.weili.iot_portal.service.ingestion.handler;

import com.weili.basic.common.enums.ErrorCodeConstants;
import com.weili.basic.common.exception.ServiceException;
import com.weili.iot_portal.common.constant.RedisConstant;
import com.weili.iot_portal.dal.dataobject.ingestion.WebhookInboxDO;
import com.weili.iot_portal.domain.ingestion.WebhookRequest;
import com.weili.iot_portal.service.cache.RealTimeCacheService;
import com.weili.iot_portal.service.support.DeviceIdentityCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String EVENT_TYPE = "DEVICE_PROGRAM";

    private final DeviceIdentityCacheService deviceIdentityCacheService;
    private final RealTimeCacheService realTimeCacheService;

    @Value("${rt.program.ttl-millis:300000}")
    private long programTtlMillis;

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equals(eventType);
    }

    @Override
    public int order() {
        return 35; // 在轴(20)/状态(10)/刀具(30)之间
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(WebhookInboxDO inbox, WebhookRequest request) throws Exception {
        Map<String, Object> eventData = request.getEventData();
        if (eventData == null || eventData.isEmpty()) {
            throw new ServiceException(ErrorCodeConstants.DEFAULT_ERROR.getCode(), "事件数据不能为空");
        }

        DeviceIdentityCacheService.DeviceIdentity identity = deviceIdentityCacheService
                .resolveByDeviceCode(request.getDeviceCode(),
                        request.getDeviceId(), "DeviceProgramEvent");
        String deviceInfoId = identity.getDeviceId();
        String orgFactoryId = identity.getFactoryId();

        Long eventTimestamp = request.getDataTimestamp() != null
                ? request.getDataTimestamp()
                : (request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis());

        Map<String, String> payload = extractProgramFields(eventData);
        if (payload.isEmpty()) {
            log.warn("DEVICE_PROGRAM 事件未包含程序字段，跳过: deviceInfoId={}", deviceInfoId);
            return;
        }
        payload.put("updatedAt", String.valueOf(eventTimestamp));
        payload.put("source", "TB");
        if (StringUtils.isNotBlank(request.getMessageId())) {
            payload.put("traceId", request.getMessageId());
        }

        String key = String.format(RedisConstant.RT_PROGRAM,
                defaultBlank(orgFactoryId), defaultBlank(deviceInfoId));
        realTimeCacheService.hsetWithTtl(key, payload, programTtlMillis);
    }

    private Map<String, String> extractProgramFields(Map<String, Object> eventData) {
        Map<String, String> map = new HashMap<>();
        eventData.forEach((k, v) -> {
            if (k == null || v == null) {
                return;
            }
            String key = k.trim();
            if ("programName".equalsIgnoreCase(key) || "program".equalsIgnoreCase(key)) {
                map.put("programName", String.valueOf(v));
            } else if ("programPath".equalsIgnoreCase(key) || "program_path".equalsIgnoreCase(key)) {
                map.put("programPath", String.valueOf(v));
            } else if ("gCode".equalsIgnoreCase(key) || "gcode".equalsIgnoreCase(key)) {
                map.put("gCode", String.valueOf(v));
            } else if ("mCode".equalsIgnoreCase(key) || "mcode".equalsIgnoreCase(key)) {
                map.put("mCode", String.valueOf(v));
            } else if (key.startsWith("program") || key.startsWith("gCode") || key.startsWith("mCode")) {
                map.put(key, String.valueOf(v));
            }
        });
        return map;
    }

    private String defaultBlank(String value) {
        return StringUtils.defaultIfBlank(value, "none");
    }
}


